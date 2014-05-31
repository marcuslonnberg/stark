package se.marcuslonnberg.loginproxy.auth

import spray.http.Uri.Query
import spray.http._
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.FicusConfig._
import spray.http.HttpRequest
import spray.client.pipelining._
import scala.Some
import scala.concurrent.{ExecutionContext, Future}
import org.json4s._
import org.json4s.JsonAST._
import se.marcuslonnberg.loginproxy.Json4sProtocol
import akka.actor.ActorRefFactory
import spray.http.HttpHeaders.{Location, `Set-Cookie`}
import se.marcuslonnberg.loginproxy.auth.AuthActor.{AuthCallback, UserInfo}
import se.marcuslonnberg.loginproxy.utils.AES
import se.marcuslonnberg.loginproxy.utils.Implicits._

trait GoogleAuth extends StateOps {

  implicit def actorRefFactory: ActorRefFactory

  implicit def executionContext: ExecutionContext

  val google = ConfigFactory.load().getConfig("auth.google")
  val clientId = google.as[String]("clientId")
  val clientSecret = google.as[String]("clientSecret")
  val stateCookieName = google.as[String]("stateCookieName")

  def initialRequest(request: HttpRequest, callbackUri: Uri, sourceUri: Uri) = {
    val state = generateState(sourceUri, request)

    val parameters = Map(
      "client_id" -> clientId,
      "response_type" -> "code",
      "scope" -> "openid email profile",
      "redirect_uri" -> callbackUri.toString(),
      "state" -> state.message)

    val uri = Uri("https://accounts.google.com/o/oauth2/auth").copy(query = Query(parameters))

    HttpResponse(
      status = StatusCodes.TemporaryRedirect,
      headers = Location(uri) :: `Set-Cookie`(state.cookie) :: Nil)
  }

  def callback(request: HttpRequest, callbackUri: Uri): Future[AuthCallback] = {
    (request.uri.query.get("code"), request.uri.query.get("state")) match {
      case (Some(code), Some(stateParam)) =>
        val state = extractState(request, stateParam)

        requestToken(code, callbackUri).map { userInfo =>
          AuthCallback(state.sourceUri, userInfo)
        }
      case _ =>
        throw new IllegalArgumentException("Missing parameter 'code' or 'state'.")
    }
  }

  private def requestToken(code: String, callbackUri: Uri): Future[UserInfo] = {
    val parameters = Map(
      "code" -> code,
      "client_id" -> clientId,
      "client_secret" -> clientSecret,
      "redirect_uri" -> callbackUri.toString(),
      "grant_type" -> "authorization_code")

    val pipeline: HttpRequest => Future[JValue] = {
      import Json4sProtocol._
      sendReceive ~> unmarshal[JValue]
    }

    val request = Post(Uri("https://accounts.google.com/o/oauth2/token"), FormData(parameters))
    pipeline(request).flatMap { tokenResponse =>
      import Json4sProtocol._
      val accessToken = (tokenResponse \ "access_token").extract[String]
      requestUserInfo(accessToken)
    }
  }

  case class UserInfoResponse(name: Option[String],
                              email: Option[String],
                              verified_email: Option[Boolean],
                              picture: Option[String],
                              gender: Option[String],
                              locale: Option[String])

  private def requestUserInfo(accessToken: String): Future[UserInfo] = {

    val pipeline: HttpRequest => Future[JValue] = {
      import Json4sProtocol._
      addHeader("Authorization", s"Bearer $accessToken") ~> (sendReceive ~> unmarshal[JValue])
    }

    pipeline(Get(Uri("https://www.googleapis.com/oauth2/v2/userinfo"))).map { json =>
      import Json4sProtocol._
      json.extract[UserInfo]
    }
  }
}

/**
 * Handles the state of the source URI when the user is authenticating. Before authentication a cookie is
 * created which contains a key. That key is used to encrypt the source URI that the user tried to access which is sent
 * to the 3rd party authenticator. The source URI is decrypted on the callback.
 *
 * The reason why the source URI is sent to the authenticator is so that if a user tries to access multiple URIs before
 * authenticating the request should go to the requested destination after authentication.
 *
 * The cookie will not be created if one already exists with the expected content length.
 */
trait StateOps {
  def stateCookieName: String

  case class State(cookie: HttpCookie, message: String) {
    def sourceUri = {
      val uri = AES.decrypt(message, cookie.content.fromBase64)
      Uri(uri)
    }
  }

  def findStateCookie(request: HttpRequest) = request.cookies.find(_.name == stateCookieName)

  def generateState(sourceUri: Uri, request: HttpRequest) = {
    val (cookie, key) = findStateCookie(request) match {
      case Some(cookie) if cookie.content.length == 24 =>
        cookie -> cookie.content.fromBase64
      case _ =>
        val key = AES.generateKey(128)
        HttpCookie(stateCookieName, key.toBase64String) -> key
    }
    val state = AES.encrypt(sourceUri.toString(), key)
    State(cookie, state)
  }

  def extractState(request: HttpRequest, state: String) = {
    findStateCookie(request) match {
      case Some(cookie) =>
        State(cookie, state)
      case None =>
        throw new IllegalArgumentException(s"Missing cookie '$stateCookieName'")
    }
  }
}
