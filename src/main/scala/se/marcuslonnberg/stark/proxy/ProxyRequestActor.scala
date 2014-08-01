package se.marcuslonnberg.stark.proxy

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import se.marcuslonnberg.stark.ConnectionActor.SiteRequest
import se.marcuslonnberg.stark.auth.AuthActor.{AuthenticatedHeader, AuthenticatedSession, UserInfo}
import se.marcuslonnberg.stark.proxy.ProxyRequestActor.{ProxyRequest, SiteRequestRouting}
import se.marcuslonnberg.stark.site.ProxyConf
import spray.http.{HttpCookie, HttpRequest}

object ProxyRequestActor {
  def props() = Props(classOf[ProxyRequestActor])

  case class ProxyRequest(request: HttpRequest, userInfo: Option[UserInfo] = None, cookie: Option[HttpCookie] = None)

  case class SiteRequestRouting(request: ProxyRequest, receiver: ActorRef)

}

class ProxyRequestActor() extends Actor with ActorLogging {
  var connections = Map.empty[ProxyConf, ActorRef]

  def receive = {
    case siteRequest@SiteRequest(request, auth, receiver, proxy: ProxyConf) =>
      val connection = connections.getOrElse(proxy, {
        val c = context.actorOf(ProxyConnectionActor.props(proxy))
        connections += (proxy -> c)
        c
      })

      auth match {
        case AuthenticatedSession(userInfo, cookie) =>
          connection ! SiteRequestRouting(ProxyRequest(request, Some(userInfo), cookie), receiver)
        case AuthenticatedHeader =>
          connection ! SiteRequestRouting(ProxyRequest(request), receiver)
      }

    //connection ! siteRequest
  }
}
