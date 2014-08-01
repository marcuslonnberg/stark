package se.marcuslonnberg.stark.site

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.pipe
import se.marcuslonnberg.stark.api.RedisProxyStorage
import se.marcuslonnberg.stark.site.SitesActor._
import spray.http._

import scala.util.{Failure, Success}

object SitesActor {

  def props() = Props(classOf[SitesActor])

  case class AddProxies(proxies: List[ProxyConf])

  case class AddSite(site: Site)

  sealed trait AddSiteResponse

  object AddSiteResponses {

    case object Added extends AddSiteResponse

    case class AddedWithProblem(reason: String) extends AddSiteResponse

    case class NotAdded(reason: String) extends AddSiteResponse

  }

  case class RemoveProxy(location: Location)

  case class RemoveProxyResponse(removed: Long)

  case class GetSiteByUri(uri: Uri)

  case class GetSiteByLocation(location: Location, onlyProxy: Boolean = false)

  case class GetSiteResponse(site: Option[Site])

  case class GetSites(onlyProxies: Boolean = false)

  case class GetSitesResponse(proxies: List[Site])

}

class SitesActor extends Actor with ActorLogging {

  import context.dispatcher

  val storage = new RedisProxyStorage {
    implicit def system = context.system
  }

  override def preStart() = {
    storage.getProxies onComplete {
      case Success(proxies) =>
        self ! AddProxies(proxies.flatten.toList)
      case Failure(ex) =>
        log.error(ex, "Could not load proxies")
    }
  }

  override def receive: Receive = state(List.empty)

  def state(sites: List[Site]): Receive = {
    case GetSiteByUri(requestUri) =>
      val site = getSite(sites, requestUri)
      sender ! GetSiteResponse(site)

    case AddProxies(newProxies) =>
      log.info("Adding {} proxies", newProxies.length)
      context.become(state(newProxies ++ sites))

    case GetSites(onlyProxies) =>
      val response =
        if (onlyProxies) sites.collect { case p: ProxyConf => p}
        else sites
      sender ! GetSitesResponse(response)

    case AddSite(site) =>
      if (sites.exists(_.location == site.location)) {
        sender ! AddSiteResponses.NotAdded(s"A site already exists with the same location (${site.location})")
      } else {
        log.info("Adding site: {}", site)
        context.become(state(site :: sites))

        site match {
          case proxy: ProxyConf =>
            storage.addProxy(proxy).map {
              case true => AddSiteResponses.Added
              case false => AddSiteResponses.AddedWithProblem("Site was added, but could not be stored to persistence.")
            } pipeTo sender()
          case _ =>
            sender ! AddSiteResponses.Added
        }
      }

    case RemoveProxy(location) =>
      val updatedProxies = sites.filterNot {
        case proxy: ProxyConf => proxy.location == location
        case _ => false
      }
      val diff = sites.diff(updatedProxies)
      log.info("Removed proxies: {}", diff)
      context.become(state(updatedProxies))
      storage.removeProxy(location).map(RemoveProxyResponse) pipeTo sender()
  }

  def getSite(sites: List[Site], requestUri: Uri): Option[Site] = {
    val requestAddress = requestUri.authority.host
    val requestPath = requestUri.path
    sites.find { site =>
      val path = requestPath.startsWith(site.location.path)
      val address = requestAddress == site.location.host
      address && path
    }
  }
}
