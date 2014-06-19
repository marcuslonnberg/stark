package se.marcuslonnberg.stark.auth.providers

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.IO
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import org.json4s._
import se.marcuslonnberg.stark.auth.AuthActor.{AuthCallback, UserInfo}
import AuthProvider._
import spray.can.Http
import spray.http.HttpHeaders.{Location, `Set-Cookie`}
import spray.http.Uri.Query
import spray.http._
import spray.httpx.marshalling._
import spray.httpx.unmarshalling._

object GoogleAuthProvider extends StateOps with AuthProvider {
  val google = ConfigFactory.load().getConfig("auth.google")
  val clientId = google.as[String]("client-id")
  val clientSecret = google.as[String]("client-secret")
  val stateCookieName = google.as[String]("state-cookie-name")

  def actorName = "google"

  def redirectBrowser(request: HttpRequest, callbackUri: Uri, sourceUri: Uri) = {
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

  def props(sender: ActorRef) = Props(classOf[GoogleAuthActor], sender)


  case class UserInfoResponse(name: Option[String],
                              email: Option[String],
                              verified_email: Option[Boolean],
                              picture: Option[String],
                              gender: Option[String],
                              locale: Option[String],
                              link: Option[String],
                              given_name: Option[String],
                              family_name: Option[String],
                              id: Option[String])

}

class GoogleAuthActor(sender: ActorRef) extends Actor with ActorLogging with StateOps {

  import context.system
  import GoogleAuthProvider._

  def stateCookieName = GoogleAuthProvider.stateCookieName

  val io = IO(Http)

  def receive = {
    case AuthResponse(request, callback) =>
      (request.uri.query.get("code"), request.uri.query.get("state")) match {
        case (Some(code), Some(stateParam)) =>
          val state = extractState(request, stateParam)

          io ! tokenRequest(code, callback)
          context.become(waitForAccessToken(state))
        case _ =>
          throw new IllegalArgumentException("Missing parameter 'code' or 'state'.")
      }
  }

  def waitForAccessToken(state: State): Receive = {
    case HttpResponse(StatusCodes.OK, entity, _, _) =>
      import se.marcuslonnberg.stark.JsonProtocol._
      val accessToken = entity.as[JObject].right.toOption
        .flatMap(obj => (obj \ "access_token").extractOpt[String])
        .getOrElse(sys.error("Could not parse access token"))

      log.debug("Got access token: {}", accessToken)
      io ! userInfoRequest(accessToken)
      context.become(waitForUserInfo(state))
    case _ =>

  }

  def waitForUserInfo(state: State): Receive = {
    case HttpResponse(_, entity, _, _) =>
      import se.marcuslonnberg.stark.JsonProtocol._
      val userInfoResponse = entity.as[JObject].right.toOption.flatMap { obj =>
        obj.extractOpt[UserInfoResponse]
      }
      val userInfo = userInfoResponse.map { u =>
        if (!u.verified_email.contains(true)) sys.error("User email is not verified")

        UserInfo(u.name, u.email, u.locale)
      } getOrElse sys.error("Could not parse user info response")

      log.debug("User info: {}", userInfo)
      context.parent.tell(AuthCallback(state.sourceUri, userInfo), sender)
      context.stop(self)
  }

  def tokenRequest(code: String, callbackUri: Uri) = {
    val parameters = Map(
      "code" -> code,
      "client_id" -> clientId,
      "client_secret" -> clientSecret,
      "redirect_uri" -> callbackUri.toString(),
      "grant_type" -> "authorization_code")

    HttpRequest(HttpMethods.POST,
      Uri("https://accounts.google.com/o/oauth2/token"),
      entity = marshalUnsafe(FormData(parameters)))
  }

  def userInfoRequest(accessToken: String) = {
    val authorizationHeader = HttpHeaders.Authorization(GenericHttpCredentials("Bearer", accessToken))
    val uri = Uri("https://www.googleapis.com/oauth2/v2/userinfo")
    HttpRequest(HttpMethods.GET, uri, List(authorizationHeader))
  }

}
