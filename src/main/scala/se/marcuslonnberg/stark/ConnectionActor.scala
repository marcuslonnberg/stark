package se.marcuslonnberg.stark

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp.ConnectionClosed
import se.marcuslonnberg.stark.ConnectionActor.SiteRequest
import se.marcuslonnberg.stark.auth.AuthActor
import se.marcuslonnberg.stark.auth.AuthActor.{AuthResponse, Authenticated}
import se.marcuslonnberg.stark.proxy.ProxyRequestActor
import se.marcuslonnberg.stark.site.Implicits._
import se.marcuslonnberg.stark.site.SitesActor.{GetSiteByUri, GetSiteResponse}
import se.marcuslonnberg.stark.site.{ActorSite, ProxyConf, Site, StaticContentConf}
import se.marcuslonnberg.stark.static.StaticContentActor
import spray.http.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}

object ConnectionActor {

  case class SiteRequest(request: HttpRequest, auth: Authenticated, receiver: ActorRef, site: Site) {
    def requestRelativePath = {
      val relativePath = request.uri.path.relativizeTo(site.location.path).get
      request.copy(uri = request.uri.copy(path = relativePath))
    }
  }

  def props(sitesActor: ActorRef) = Props(classOf[ConnectionActor], sitesActor)
}

class ConnectionActor(sitesActor: ActorRef) extends Actor with ActorLogging {
  val authActor = context.actorOf(AuthActor.props(sitesActor), "auth")

  lazy val proxyConnectionsActor = context.actorOf(ProxyRequestActor.props(), "proxies")

  log.debug("Connected")

  def receive = initialRequest orElse closeConnection

  def initialRequest: Receive = {
    case request: HttpRequest =>
      authActor ! request
      context.become(checkAuth(request, sender()))
  }

  def checkAuth(request: HttpRequest, receiver: ActorRef): Receive = {
    case AuthResponse(response) =>
      log.debug("Auth response: {}", response)
      receiver ! response
      context.become(initialRequest orElse closeConnection)

    case auth: Authenticated =>
      log.debug("User is authenticated")
      sitesActor ! GetSiteByUri(request.uri)
      context.become(getSite(request, receiver, auth))
  }

  def getSite(request: HttpRequest, receiver: ActorRef, auth: Authenticated): Receive = {
    case GetSiteResponse(None) =>
      receiver ! HttpResponse(StatusCodes.NotFound, HttpEntity("Site was not found"))
      context.become(initialRequest orElse closeConnection)

    case GetSiteResponse(Some(site)) =>
      log.debug("Found site: {}", site)
      val siteRequest = SiteRequest(request, auth, receiver, site)

      site match {
        case actorSite: ActorSite =>
          context.actorSelection(actorSite.recipient) ! siteRequest
        case proxySite: ProxyConf =>
          proxyConnectionsActor ! siteRequest
        case staticSite: StaticContentConf =>
          context.actorOf(StaticContentActor.props(staticSite)) ! siteRequest
        case _ =>
          log.error("Unknown site type")
          receiver ! HttpResponse(StatusCodes.InternalServerError, HttpEntity("Unknown site type"))
      }

      context.become(initialRequest orElse closeConnection)
  }

  def closeConnection: Receive = {
    case close: ConnectionClosed =>
      log.debug("Connection closed")
      context.stop(self)
  }

  override def unhandled(message: Any) = {
    log.warning("Unhandled: {}", message)
  }
}
