package se.marcuslonnberg.loginproxy.proxy

import akka.actor.{Props, Actor, ActorLogging}
import spray.http._
import spray.http.HttpRequest
import se.marcuslonnberg.loginproxy.proxy.ProxyActor.{SetProxies, ProxyRequest}
import se.marcuslonnberg.loginproxy.auth.AuthActor.UserInfo
import se.marcuslonnberg.loginproxy.api.ApiActor

object ProxyActor {

  def props(apiDomain: Uri.Host) = Props(classOf[ProxyActor], apiDomain)

  case class ProxyRequest(request: HttpRequest, userInfo: UserInfo, cookie: Option[HttpCookie])

  case class SetProxies(proxies: List[ProxyConf])

}

class ProxyActor(apiDomain: Uri.Host) extends Actor with ActorLogging {
  val apiRoutingActor = context.actorOf(ApiActor.props(self), "api-routing")

  override def receive: Receive = state(List.empty)

  def state(proxies: List[ProxyConf]): Receive = {
    case ProxyRequest(request, _, _) if request.uri.authority.host == apiDomain =>
      apiRoutingActor.tell(request, sender())
    case request: ProxyRequest =>
      context.actorOf(ProxyRequestActor.props(request, proxies, sender()))
    case SetProxies(newProxies) =>
      context.become(state(newProxies))
  }
}
