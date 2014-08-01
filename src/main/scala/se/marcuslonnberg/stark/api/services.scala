package se.marcuslonnberg.stark.api

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import se.marcuslonnberg.stark.JsonSupport
import se.marcuslonnberg.stark.auth.storage.RedisAuthStore
import se.marcuslonnberg.stark.site.SitesActor._
import se.marcuslonnberg.stark.site.{Location, ProxyConf}
import se.marcuslonnberg.stark.utils.Directives._
import spray.http.StatusCodes
import spray.routing.HttpService

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait ProxiesApiService extends HttpService with JsonSupport {
  def sitesActor: ActorRef

  implicit def executionContext: ExecutionContext

  implicit val timeout = Timeout(5.seconds)

  def proxiesEnabled: Boolean

  def proxiesRoute = pathPrefix("proxies") {
    forbiddenWhen(!proxiesEnabled)("Proxies API is disabled") ~
      pathEndOrSingleSlash {
        get {
          onSuccess(sitesActor ? GetSites()) {
            case GetSitesResponse(proxies) =>
              complete(proxies)
          }
        } ~
          post {
            entity(as[ProxyConf]) { proxy =>
              onSuccess(sitesActor ? AddSite(proxy)) {
                case AddSiteResponses.Added =>
                  complete(proxy)
                case AddSiteResponses.NotAdded(reason) =>
                  complete(StatusCodes.BadRequest, reason)
                case AddSiteResponses.AddedWithProblem(reason) =>
                  complete(StatusCodes.ServerError, reason)
              }
            }
          }
      } ~
      path(Rest) { proxyUri =>
        val location = Location(proxyUri)
        get {
          onSuccess(sitesActor ? GetSiteByLocation(location)) {
            case GetSiteResponse(proxy) => complete(proxy)
          }
        } ~
          delete {
            onSuccess(sitesActor ? RemoveProxy(location)) {
              case RemoveProxyResponse(0) =>
                complete(StatusCodes.NotFound)
              case RemoveProxyResponse(i) =>
                complete(StatusCodes.OK, i)
            }
          }
      }
  }
}

trait AuthSessionsApiService extends HttpService with JsonSupport {
  this: Actor =>

  implicit def executionContext: ExecutionContext

  def redis: RedisAuthStore

  def sessionsEnabled: Boolean

  def sessionsRoute = pathPrefix("sessions") {
    forbiddenWhen(!sessionsEnabled)("Sessions API is disabled") ~
      pathEndOrSingleSlash {
        get {
          onSuccess(redis.getSessions) {
            case sessions => complete(sessions)
          }
        }
      } ~
      path("ids") {
        get {
          onSuccess(redis.sessionIds) {
            case sessions => complete(sessions)
          }
        }
      } ~
      path(Segment) { session =>
        get {
          complete {
            redis.getSession(session)
          }
        } ~
          delete {
            onSuccess(redis.removeHeader(session)) {
              case _ => complete(StatusCodes.OK)
            }
          }
      }
  }
}

trait AuthHeadersApiService extends HttpService with JsonSupport {
  this: Actor =>

  implicit def executionContext: ExecutionContext

  def redis: RedisAuthStore

  def headersEnabled: Boolean

  def headersRoute = pathPrefix("headers") {
    forbiddenWhen(!headersEnabled)("Headers API is disabled") ~
      pathEndOrSingleSlash {
        (get & complete) {
          redis.getHeaders
        }
      } ~
      path(Segment) { header =>
        (delete & complete) {
          redis.removeHeader(header)
        } ~
          (post & complete) {
            redis.saveHeader(header)
          }
      }
  }
}
