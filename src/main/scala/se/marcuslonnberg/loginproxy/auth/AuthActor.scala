package se.marcuslonnberg.loginproxy.auth

import akka.actor.{Props, Actor, ActorLogging}
import spray.http._
import spray.http.HttpRequest
import spray.http.HttpResponse
import se.marcuslonnberg.loginproxy.auth.AuthActor._
import scala.Some
import scala.util.{Random, Failure, Success}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.FicusConfig._
import scala.concurrent.duration._

object AuthActor {
  def props(): Props = Props(classOf[AuthActor])

  case class Callback(callbackRequest: HttpRequest)

  case class UserInfo(name: Option[String],
                      email: Option[String],
                      locale: Option[String])

  case class AuthInfo(user: UserInfo, expires: Option[DateTime])

  case class AuthResponse(response: HttpResponse)

  case class LoggedIn(userInfo: UserInfo, cookie: Option[HttpCookie] = None)

  case class AuthCallback(requestUri: Uri, userInfo: UserInfo)

}

class AuthActor extends Actor with ActorLogging {
  val config = ConfigFactory.load().getConfig("auth")

  val CookieName = config.as[String]("cookieName")
  val CallbackUri = Uri(config.as[String]("callbackUrl"))
  val CheckUri = Uri(config.as[String]("checkUrl"))
  val SetCookiePath = Uri.Path(config.as[String]("setCookiePath"))
  val SourceParameter = "source"
  val CookieParameter = "cookie"

  override def receive = state(Map(), Map())

  import context.dispatcher

  val Google = new GoogleAuth {
    override implicit def actorRefFactory = context

    override implicit def executionContext = context.dispatcher
  }

  case class StoreLogin(requestUri: Uri, userInfo: UserInfo)
  
  def state(cookieAuths: Map[String, AuthInfo], headerAuths: Map[String, AuthInfo]): Receive = {
    case request@HttpRequest(HttpMethods.GET, uri, _, _, _) if isSetCookieUri(uri) =>
      (request.uri.query.get(SourceParameter), request.uri.query.get(CookieParameter)) match {
        case (Some(source), Some(cookieValue)) =>
          // Set cookie on source domain and redirect to source url
          sender ! AuthResponse(RedirectSetCookie(Uri(source), cookieValue))
      }
    case request@HttpRequest(HttpMethods.GET, uri, _, _, _) if isCheckUri(uri) =>
      val loginCookie = getLoginCookie(request)

      val authInfoOption = loginCookie.flatMap { cookie =>
        cookieAuths.get(cookie.content)
      }

      authInfoOption match {
        case Some(authInfo) =>
          log.debug("Auth info", authInfo)

          request.uri.query.get(SourceParameter) match {
            case Some(source) =>
              val sourceUri = Uri(source)
              val redirectionUri = sourceUri.withPath(SetCookiePath)
                .withQuery(SourceParameter -> source, CookieParameter -> loginCookie.get.content) // TODO: propertly get the cookie
            val redirect = HttpResponse(
                status = StatusCodes.TemporaryRedirect,
                headers = HttpHeaders.Location(redirectionUri) :: Nil)
              sender ! AuthResponse(redirect)
            case None =>
              val response = HttpResponse(
                status = StatusCodes.BadRequest,
                entity = s"Missing '$SourceParameter' parameter")
              sender ! AuthResponse(response)
          }
        case _ =>
          if (request.method == HttpMethods.GET) {
            sender ! AuthResponse(Google.initialRequest(CallbackUri, request.uri))
          } else {
            sender ! AuthResponse(HttpResponse(StatusCodes.Unauthorized, "Log in with a GET request"))
          }
      }
    case request@HttpRequest(HttpMethods.GET, uri, _, _, _) if isCallbackUri(uri) =>
      val sender = context.sender()
      Google.callback(request, CallbackUri).onComplete {
        case Success(AuthCallback(requestUri, userInfo)) =>
          self.tell(StoreLogin(requestUri, userInfo), sender)
        case Failure(ex) =>
          log.error(ex, "Error in auth callback")
          sender ! HttpResponse(StatusCodes.InternalServerError, "Error in auth")
      }
    case request: HttpRequest =>
      val loginCookie = getLoginCookie(request)

      val authInfoOption = loginCookie.flatMap { cookie =>
        cookieAuths.get(cookie.content)
      }

      authInfoOption match {
        case Some(authInfo) =>
          log.debug("Auth info", authInfo)
          sender ! LoggedIn(authInfo.user)
        case _ =>
          val redirectionUri = CheckUri.withQuery((SourceParameter -> request.uri.toString()) +: CheckUri.query)
          val redirect = HttpResponse(
            status = StatusCodes.TemporaryRedirect,
            headers = HttpHeaders.Location(redirectionUri) :: Nil)

          sender ! AuthResponse(redirect)
      }
    case StoreLogin(requestUri, userInfo) =>
      val code = Random.nextLong().toString
      val newCookieAuths = cookieAuths + (code -> AuthInfo(userInfo, expires = None))
      context.become(state(newCookieAuths, headerAuths))

      // Set cookie on login domain and redirect to source URI
      sender ! AuthResponse(RedirectSetCookie(requestUri, code))
  }

  def RedirectSetCookie(redirectionUri: Uri, cookieValue: String) = {
    val cookie = HttpCookie(CookieName, cookieValue, expires = Some(DateTime.now + 30.days.toMillis), path = Some("/"))
    HttpResponse(
      status = StatusCodes.SeeOther,
      headers = HttpHeaders.Location(redirectionUri) :: HttpHeaders.`Set-Cookie`(cookie) :: Nil)
  }

  def getLoginCookie(request: HttpRequest) = request.cookies.find(_.name == CookieName)

  def isUri(baseUri: Uri)(uri: Uri): Boolean = uri.authority == baseUri.authority && uri.path == baseUri.path

  def isCallbackUri(uri: Uri): Boolean = isUri(CallbackUri)(uri)

  def isCheckUri(uri: Uri): Boolean = isUri(CheckUri)(uri)

  def isSetCookieUri(uri: Uri) = {
    uri.path == SetCookiePath &&
      uri.query.get(SourceParameter).isDefined &&
      uri.query.get(CookieParameter).isDefined
  }
}
