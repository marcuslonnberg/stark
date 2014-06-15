package se.marcuslonnberg.stark.api

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import se.marcuslonnberg.stark.api.ProxyApiActor._
import se.marcuslonnberg.stark.proxy.ProxiesActor.SetProxies
import se.marcuslonnberg.stark.proxy.ProxyConf
import spray.http.Uri

object ProxyApiActor {
  def props(proxyActor: ActorRef) = Props(classOf[ProxyApiActor], proxyActor)

  case class AddProxy(proxy: ProxyConf)

  case object GetProxies

  case class GetProxy(host: Uri.Host, path: Uri.Path)

  object Responses {

    case class AddProxy(proxy: ProxyConf)

    case class GetProxies(proxies: List[ProxyConf])

    case class GetProxy(proxy: Option[ProxyConf])

  }

}

class ProxyApiActor(proxyActor: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = state(List.empty)

  def state(proxies: List[ProxyConf]): Receive = {
    case GetProxies =>
      sender ! Responses.GetProxies(proxies)
    case AddProxy(proxy) =>
      log.info("Adding proxy {}", proxy)
      val newProxies = proxy +: proxies
      proxyActor ! SetProxies(newProxies)
      context.become(state(newProxies))
      sender ! Responses.AddProxy(proxy)
    case GetProxy(host, path) =>
      val proxy = proxies.find(p => p.host == host || p.path == path)
      sender ! Responses.GetProxy(proxy)
  }
}
