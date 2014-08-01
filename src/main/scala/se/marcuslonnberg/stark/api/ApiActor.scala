package se.marcuslonnberg.stark.api

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import se.marcuslonnberg.stark.ConnectionActor.SiteRequest
import se.marcuslonnberg.stark.auth.storage.RedisAuthStore
import se.marcuslonnberg.stark.site.SitesActor.AddSite
import se.marcuslonnberg.stark.site.{ApiConf, Location}

import scala.concurrent.ExecutionContext

object ApiActor {
  def props(proxiesApiActor: ActorRef) = Props(classOf[ApiActor], proxiesApiActor)
}

class ApiActor(val sitesActor: ActorRef) extends Actor with ProxiesApiService with AuthHeadersApiService with AuthSessionsApiService {
  override implicit def actorRefFactory = context

  override implicit def executionContext: ExecutionContext = context.dispatcher

  override val redis = new RedisAuthStore {
    implicit def system = context.system
  }

  val config = ConfigFactory.load().getConfig("api")

  val proxiesEnabled = config.as[Boolean]("proxies")

  val sessionsEnabled = config.as[Boolean]("sessions")

  val headersEnabled = config.as[Boolean]("headers")

  def receive = receiveSiteRequest orElse runRoute(proxiesRoute ~ sessionsRoute ~ headersRoute)

  def receiveSiteRequest: Receive = {
    case siteRequest: SiteRequest =>
      self.tell(siteRequest.requestRelativePath, siteRequest.receiver)
  }

  override def preStart() = {
    config.as[Option[String]]("site-location") foreach { locationString =>
      val location = Location(locationString)
      sitesActor ! AddSite(ApiConf(location, self.path))
    }
  }
}
