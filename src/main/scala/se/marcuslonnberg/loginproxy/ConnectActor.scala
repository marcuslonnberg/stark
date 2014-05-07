package se.marcuslonnberg.loginproxy

import akka.actor.{ActorLogging, Actor}
import spray.can.Http
import se.marcuslonnberg.loginproxy.ConnectActor._
import se.marcuslonnberg.loginproxy.proxy.{ProxyConf, ProxyActor}

object ConnectActor {

  case class SetProxies(proxies: List[ProxyConf])

}

class ConnectActor extends Actor with ActorLogging {
  override def receive = state(List())

  def state(proxies: List[ProxyConf]): Receive = {
    case _: Http.Connected =>
      val proxy = context.actorOf(ProxyActor.props(proxies))
      sender ! Http.Register(proxy)

    case SetProxies(newProxies: List[ProxyConf]) =>
      log.info("Changing proxies to ({}):\n{}", newProxies.size, newProxies.mkString("\n"))
      context.become(state(newProxies))
  }
}
