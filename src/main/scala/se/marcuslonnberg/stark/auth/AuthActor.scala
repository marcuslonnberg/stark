package se.marcuslonnberg.stark.auth

import java.security.SecureRandom

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.pipe
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import se.marcuslonnberg.stark.auth.AuthActor._
import se.marcuslonnberg.stark.auth.providers.{AuthProviderRequestActor, AuthProvider, GoogleAuthProvider}
import se.marcuslonnberg.stark.auth.storage.RedisAuthStore
import se.marcuslonnberg.stark.utils.Implicits._
import spray.http.StatusCodes.{Redirection, TemporaryRedirect}
import spray.http.{DateTime => SprayDateTime, _}
import com.github.nscala_time.time.Imports._
import se.marcuslonnberg.stark.utils._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.matching.Regex

object AuthActor {

  case class AuthInfo(user: UserInfo, expires: Option[DateTime])

  case object NotAuthenticated

  sealed trait Authenticated

  case object AuthenticatedHeader extends Authenticated

  case class AuthenticatedSession(userInfo: UserInfo, cookie: Option[HttpCookie] = None) extends Authenticated

  case class UserInfo(name: Option[String],
                      email: Option[String],
                      locale: Option[String])

  case class AuthResponse(response: HttpResponse)

  case class AuthCallback(requestUri: Uri, userInfo: UserInfo)

  def props() = Props[AuthActor]()

}

class AuthActor extends Actor with ActorLogging with CookieAuth with HeaderAuth with SecureRandomIdGenerator with PathRequests with RedisAuthStore {

  import context.dispatcher

  implicit lazy val system = context.system
  val config = ConfigFactory.load().getConfig("auth")

  val authCookieName: String = config.as[String]("cookie-name")
  val authHeaderName: String = config.as[String]("header-name")
  val callbackUri: Uri = Uri(config.as[String]("callback-uri"))
  val checkUriBase: Uri = Uri(config.as[String]("check-uri"))
  val setCookiePath: Uri.Path = Uri.Path(config.as[String]("set-cookie-path"))
  val cookieParameter: String = config.as[String]("cookie-parameter")
  val sourceUriParameter: String = config.as[String]("source-uri-parameter")
  val allowedEmailsRegex: Option[Regex] = config.as[Option[String]]("allowed-emails-regex").map(_.r)
  val sessionExpiration: Duration = config.as[Option[FiniteDuration]]("session-expiration").getOrElse(Duration.Inf)
  val authProvider: AuthProvider = {
    config.as[String]("provider") match {
      case "google" => GoogleAuthProvider
      case provider => sys.error(s"Can't resolve provider '$provider'")
    }
  }

  def checkUri(source: Uri) = checkUriBase.withQuery(sourceUriParameter -> source.toString())

  def receive = authRequest orElse regularRequest

  def regularRequest: Receive = {
    case request: HttpRequest =>
      getAuthHeader(request) match {
        case Some(header) =>
          log.debug("Found auth header: {}", header)
          authorizedHeader(header).map {
            case true => AuthenticatedHeader
            case false => NotAuthenticated
          } pipeTo self
          context.become(checkAuthenticated(request)) // TODO header?
        case None =>
          getAuthCookie(request) match {
            case Success(cookie) =>
              log.debug("Found auth cookie: {}", cookie)
              getSession(cookie.content) pipeTo self
              context.become(checkAuthenticated(request))
            case Failure(_) =>
              log.debug("No cookie or header in request, making check request")
              val redirect = redirectionResponse(TemporaryRedirect, checkUri(request.uri))
              context.parent ! AuthResponse(redirect)
          }
      }
  }

  def authRequest: Receive = {
    case request: HttpRequest if isCallbackRequest(request) =>
      log.debug("Callback request: {}", request.uri)
      val requestActor = context.actorOf(AuthProviderRequestActor.props(authProvider, sender()), "provider")
      requestActor ! AuthProvider.AuthResponse(request, callbackUri)
      context.become(authProviderResponse(request))

    case request: HttpRequest if isSetCookieRequest(request) =>
      log.debug("Set cookie request: {}", request.uri)
      getSetCookieInfo(request) match {
        case Success(SetCookieInfo(cookie, uri)) =>
          val redirect = redirectionResponse(TemporaryRedirect, uri) + setAuthCookieHeader(cookie, None)
          context.parent ! AuthResponse(redirect)
        case Failure(message) =>
          val response = unauthorizedResponse(message)
          context.parent ! AuthResponse(response)
      }

    case request: HttpRequest if isCheckRequest(request) =>
      log.debug("Check request: {}", request.uri)
      getCheckRequestInfo(request) match {
        case Success(CheckRequestInfo(Some(cookie), uri)) =>
          getSession(cookie.content) pipeTo self
          context.become(checkCookie(request, uri, cookie))
        case Success(CheckRequestInfo(None, uri)) =>
          log.debug("Missing auth cookie, redirecting to auth provider")
          val redirect = authProvider.redirectBrowser(request, callbackUri, uri)
          context.parent ! AuthResponse(redirect)
        case Failure(message) =>
          val response = unauthorizedResponse(message)
          context.parent ! AuthResponse(response)
      }
  }

  def checkAuthenticated(request: HttpRequest): Receive = {
    case Some(authInfo: AuthInfo) =>
      log.debug("Already authenticated: {}", authInfo)
      context.parent ! AuthenticatedSession(authInfo.user)
      context.become(receive)
    case None =>
      log.debug("Not authenticated")
      val redirect = redirectionResponse(TemporaryRedirect, checkUri(request.uri))
      context.parent ! AuthResponse(redirect)
      context.become(receive)
  }

  def checkCookie(request: HttpRequest, sourceUri: Uri, cookie: HttpCookie): Receive = {
    case Some(authInfo: AuthInfo) =>
      log.debug("Found valid cookie: {}", authInfo)
      val redirectionUri = sourceUri.withPath(setCookiePath)
        .withQuery(sourceUriParameter -> sourceUri.toString(), cookieParameter -> cookie.content)
      val redirect = redirectionResponse(TemporaryRedirect, redirectionUri)
      context.parent ! AuthResponse(redirect)
      context.become(receive)
    case None =>
      log.debug("Checked for cookie, none found. Sending user to auth provider")
      val redirect = authProvider.redirectBrowser(request, callbackUri, sourceUri)

      context.parent ! AuthResponse(redirect)
      context.become(receive)
  }

  case class SaveSessionResult(saved: Boolean)

  def authProviderResponse(request: HttpRequest): Receive = {
    case AuthCallback(sourceUri, userInfo) =>
      if (isAuthorized(userInfo)) {
        val id = generateId
        log.info("Auth provider callback, generating id '{}' for: {}", id, userInfo)
        val expiration = sessionExpiration match {
          case Duration.Inf => None
          case _ => Some(DateTime.now + sessionExpiration.toMillis)
        }
        saveSession(id, AuthInfo(userInfo, expiration)).map(SaveSessionResult) pipeTo self
        context.become(saveSession(request, sourceUri, id, expiration))
      } else {
        log.info("User is not authorized: {}", userInfo)
        val response = unauthorizedResponse("Permission denied!")
        context.parent ! AuthResponse(response)
        context.become(receive)
      }
  }

  def isAuthorized(userInfo: UserInfo) = {
    (allowedEmailsRegex, userInfo.email) match {
      case (Some(allowedEmailsRegex), Some(email)) => allowedEmailsRegex.findFirstIn(email).isDefined
      case (Some(_), _) => false
      case (None, _) => true
    }
  }

  def saveSession(request: HttpRequest, sourceUri: Uri, id: String, expiration: Option[DateTime]): Receive = {
    case SaveSessionResult(true) =>
      val redirect = redirectionResponse(TemporaryRedirect, sourceUri) + setAuthCookieHeader(id, expiration)
      context.parent ! AuthResponse(redirect)
      context.become(receive)
    case SaveSessionResult(false) =>
      log.error("Could not save session")
      val response = HttpResponse(StatusCodes.InternalServerError, "Could create session")
      context.parent ! AuthResponse(response)
      context.become(receive)
  }

  def redirectionResponse(statusCode: Redirection, uri: Uri): HttpResponse = {
    HttpResponse(statusCode, headers = List(HttpHeaders.Location(uri)))
  }

  def unauthorizedResponse(message: String): HttpResponse = {
    HttpResponse(StatusCodes.Unauthorized, message)
  }

  override def unhandled(message: Any) = {
    log.warning("Unhandled message: {}", message)
  }
}

trait PathRequests {
  this: CookieAuth =>

  def callbackUri: Uri

  def checkUriBase: Uri

  def cookieParameter: String

  def sourceUriParameter: String

  def setCookiePath: Uri.Path

  case class SetCookieInfo(cookieValue: String, source: Uri)

  case class CheckRequestInfo(cookie: Option[HttpCookie], source: Uri)

  def getSetCookieInfo(request: HttpRequest): Validation[SetCookieInfo, String] = {
    for {
      sourceUri <- getParameter(sourceUriParameter)(request)
      cookie <- getParameter(cookieParameter)(request)
    } yield SetCookieInfo(cookie, Uri(sourceUri))
  }

  def getCheckRequestInfo(request: HttpRequest): Validation[CheckRequestInfo, String] = {
    for {
      sourceUri <- getParameter(sourceUriParameter)(request)
    } yield {
      val cookie = getAuthCookie(request)
      CheckRequestInfo(cookie.toOption, Uri(sourceUri))
    }
  }

  def isUri(baseUri: Uri)(uri: Uri): Boolean = uri.authority == baseUri.authority && uri.path == baseUri.path

  def isCallbackRequest(request: HttpRequest): Boolean = isUri(callbackUri)(request.uri)

  def isCheckRequest(request: HttpRequest): Boolean = isUri(checkUriBase)(request.uri)

  def isSetCookieRequest(request: HttpRequest) = {
    val uri = request.uri
    uri.path == setCookiePath &&
      uri.query.get(sourceUriParameter).isDefined &&
      uri.query.get(cookieParameter).isDefined
  }
}

trait SecureRandomIdGenerator {
  private val random = new SecureRandom()

  def generateId = BigInt(130, random).toString(32)
}

trait CookieAuth extends Utils {
  def authCookieName: String

  def getAuthCookie(request: HttpRequest) = getCookie(authCookieName)(request)

  def setAuthCookieHeader(content: String, expiration: Option[DateTime]) = {
    val expirationSprayDate = expiration.map(d => SprayDateTime(d.millis))
    val expirationInSeconds = expiration.map(date => (date - DateTime.now.millis).millis / 100)

    val cookie = HttpCookie(authCookieName, content,
      expires = expirationSprayDate,
      maxAge = expirationInSeconds,
      path = Some("/"),
      httpOnly = true)
    HttpHeaders.`Set-Cookie`(cookie)
  }
}

trait HeaderAuth {
  def authHeaderName: String

  def getAuthHeader(request: HttpRequest) = request.headers.collectFirst {
    case HttpHeader(name, credentials) if name == authHeaderName => credentials
  }
}

trait Utils {
  def getCookie(cookieName: String)(request: HttpRequest): Validation[HttpCookie, String] = {
    request.cookies.find(_.name == cookieName) match {
      case Some(cookie) =>
        Success(cookie)
      case None =>
        Failure(s"Missing $cookieName cookie.")
    }
  }

  def getParameter(parameterName: String)(request: HttpRequest): Validation[String, String] = {
    request.uri.query.get(parameterName) match {
      case Some(value) =>
        Success(value)
      case None =>
        Failure(s"Missing $parameterName parameter.")
    }
  }
}
