package se.marcuslonnberg.stark.proxy

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.pipe
import se.marcuslonnberg.stark.api.RedisProxyStorage
import se.marcuslonnberg.stark.proxy.ProxiesActor._
import spray.http._

import scala.util.{Failure, Success}

object ProxiesActor {

  def props() = Props(classOf[ProxiesActor])

  case class SetProxies(proxies: List[ProxyConf])

  case class AddProxy(proxy: ProxyConf)

  sealed trait AddProxyResponse

  object AddProxyResponses {

    case object Added extends AddProxyResponse

    case class AddedWithProblem(reason: String) extends AddProxyResponse

    case class NotAdded(reason: String) extends AddProxyResponse

  }

  case class RemoveProxy(location: ProxyLocation)

  case class RemoveProxyResponse(removed: Long)

  case class ProxyFor(requestUri: Uri)

  case class ProxyForResponse(proxy: Option[ProxyConf])

  case class GetProxy(location: ProxyLocation)

  case class GetProxyResponse(proxy: Option[ProxyConf])

  case object GetProxies

  case class GetProxiesResponse(proxies: List[ProxyConf])

}

class ProxiesActor extends Actor with ActorLogging {

  import context.dispatcher

  val storage = new RedisProxyStorage {
    implicit def system = context.system
  }

  override def preStart() = {
    storage.getProxies onComplete {
      case Success(proxies) =>
        self ! SetProxies(proxies.flatten.toList)
      case Failure(ex) =>
        log.error(ex, "Could not load proxies")
    }
  }

  override def receive: Receive = state(List.empty)

  def state(proxies: List[ProxyConf]): Receive = {
    case ProxyFor(requestUri) =>
      val proxy = getProxy(proxies, requestUri)
      sender ! ProxyForResponse(proxy)

    case SetProxies(newProxies) =>
      log.info("Setting new proxies. {} proxies total", newProxies.length)
      context.become(state(newProxies))

    case GetProxies =>
      sender ! GetProxiesResponse(proxies)

    case AddProxy(proxy) =>
      if (proxies.exists(_.location == proxy.location)) {
        sender ! AddProxyResponses.NotAdded(s"A Proxy already exists with the same location (${proxy.location})")
      } else {
        log.info("Adding proxy: {}", proxy)
        context.become(state(proxy :: proxies))
        storage.addProxy(proxy).map {
          case true => AddProxyResponses.Added
          case false => AddProxyResponses.AddedWithProblem("Proxy was added, but could not be saved to persistence.")
        } pipeTo sender()
      }

    case RemoveProxy(location) =>
      val updatedProxies = proxies.filterNot(_.location == location)
      val diff = proxies.diff(updatedProxies)
      log.info("Removed proxies: {}", diff)
      context.become(state(updatedProxies))
      storage.removeProxy(location).map(RemoveProxyResponse) pipeTo sender()
  }

  def getProxy(proxies: List[ProxyConf], requestUri: Uri): Option[ProxyConf] = {
    val requestAddress = requestUri.authority.host
    val requestPath = requestUri.path
    proxies.find { proxy =>
      val path = requestPath.startsWith(proxy.location.path)
      val address = requestAddress == proxy.location.host
      address && path
    }
  }
}
