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
import org.json4s.JsonDSL._
import org.json4s.JsonAST._
import se.marcuslonnberg.loginproxy.Json4sProtocol
import akka.actor.ActorRefFactory
import spray.http.HttpHeaders.Location
import se.marcuslonnberg.loginproxy.auth.AuthActor.{AuthCallback, UserInfo}

trait GoogleAuth {

  implicit def actorRefFactory: ActorRefFactory

  implicit def executionContext: ExecutionContext

  val google = ConfigFactory.load().getConfig("auth.google")
  val clientId = google.as[String]("clientId")
  val clientSecret = google.as[String]("clientSecret")

  val SecurityToken = "123"

  def initialRequest(callbackUri: Uri, sourceUri: Uri) = {
    val parameters = Map(
      "client_id" -> clientId,
      "response_type" -> "code",
      "scope" -> "openid email profile",
      "redirect_uri" -> callbackUri.toString(),
      "state" -> (SecurityToken + sourceUri.toString())) // TODO: generate security token and encrypt sourceUri

    val uri = Uri("https://accounts.google.com/o/oauth2/auth").copy(query = Query(parameters))

    HttpResponse(
      status = StatusCodes.TemporaryRedirect,
      headers = Location(uri) :: Nil)
  }

  def callback(request: HttpRequest, callbackUri: Uri): Future[AuthCallback] = {
    (request.uri.query.get("code"), request.uri.query.get("state")) match {
      case (Some(code), Some(state)) =>
        require(state.startsWith(SecurityToken), "Security token invalid")
        val sourceUri = Uri(state.substring(SecurityToken.length))
        requestToken(code, callbackUri).map { userInfo =>
          AuthCallback(sourceUri, userInfo)
        }
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
      val name = (json \ "name").extractOpt[String]
      val email = (json \ "email").extractOpt[String]
      val locale = (json \ "locale").extractOpt[String]
      UserInfo(name, email, locale)
    }
  }
}
