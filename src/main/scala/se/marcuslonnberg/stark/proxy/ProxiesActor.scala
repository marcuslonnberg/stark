package se.marcuslonnberg.stark.proxy

import akka.actor.{Actor, ActorLogging, Props}
import se.marcuslonnberg.stark.auth.AuthActor.UserInfo
import se.marcuslonnberg.stark.proxy.ProxiesActor.{NoProxyConfFound, ProxyFor, SetProxies}
import spray.http._

object ProxiesActor {

  def props(apiHost: Uri.Host) = Props(classOf[ProxiesActor], apiHost)

  case class ProxyRequest(request: HttpRequest, userInfo: Option[UserInfo] = None, cookie: Option[HttpCookie] = None)

  case class SetProxies(proxies: List[ProxyConf])

  case class ProxyFor(requestUri: Uri)

  case object NoProxyConfFound

}

class ProxiesActor(apiHost: Uri.Host) extends Actor with ActorLogging {
  override def receive: Receive = state(List.empty)

  def state(proxies: List[ProxyConf]): Receive = {
    case SetProxies(newProxies) =>
      context.become(state(newProxies))
    case ProxyFor(requestUri) =>
      val response = getProxy(proxies, requestUri) match {
        case Some(proxy) => proxy
        case None => NoProxyConfFound
      }
      sender ! response
  }

  def getProxy(proxies: List[ProxyConf], requestUri: Uri): Option[ProxyConf] = {
    val requestAddress = requestUri.authority.host
    val requestPath = requestUri.path
    proxies.find { proxy =>
      val path = requestPath.startsWith(proxy.path)
      val address = requestAddress == proxy.host
      address && path
    }
  }
}
