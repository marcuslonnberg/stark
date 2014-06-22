package se.marcuslonnberg.stark.proxy

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import se.marcuslonnberg.stark.auth.AuthActor.UserInfo
import se.marcuslonnberg.stark.proxy.ProxiesActor.{ProxyFor, ProxyForResponse}
import se.marcuslonnberg.stark.proxy.ProxyRequestActor.ProxyRequestRouting
import spray.http.{HttpCookie, HttpRequest, HttpResponse, StatusCodes}

object ProxyRequestActor {
  def props(proxiesRef: ActorRef) = Props(classOf[ProxyRequestActor], proxiesRef)

  case class ProxyRequest(request: HttpRequest, userInfo: Option[UserInfo] = None, cookie: Option[HttpCookie] = None)

  case class ProxyRequestRouting(request: ProxyRequest, receiver: ActorRef)
}

class ProxyRequestActor(proxiesRef: ActorRef) extends Actor with ActorLogging {
  def receive = receiveRequest

  var connections = Map.empty[ProxyConf, ActorRef]

  def receiveRequest: Receive = {
    case routing@ProxyRequestRouting(request, _) =>
      log.debug("Requesting proxy for {}", request.request.uri)
      proxiesRef ! ProxyFor(request.request.uri)
      context.become(waitForProxyConf(routing))
  }

  def waitForProxyConf(routing: ProxyRequestRouting): Receive = {
    case ProxyForResponse(Some(proxy)) =>
      log.debug("Found proxy: {}", proxy)

      val connection = connections.getOrElse(proxy, {
        val c = context.actorOf(ProxyConnectionActor.props(proxy))
        connections += (proxy -> c)
        c
      })

      connection ! routing
      context.become(receiveRequest)
    case ProxyForResponse(None) =>
      log.debug("Could not find any proxy")
      routing.receiver ! HttpResponse(StatusCodes.NotFound, s"No proxy for ${routing.request.request.uri}")
      context.become(receiveRequest)
  }
}
