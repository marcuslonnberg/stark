package se.marcuslonnberg.stark.auth

import akka.actor.{Props, ActorRef}
import se.marcuslonnberg.stark.utils.AES
import spray.http.{HttpResponse, HttpRequest, Uri, HttpCookie}
import se.marcuslonnberg.stark.utils.Implicits._

object AuthProvider {

  case class AuthResponse(request: HttpRequest, callbackUri: Uri)

}

trait AuthProvider {
  def redirectBrowser(request: HttpRequest, callbackUri: Uri, sourceUri: Uri): HttpResponse

  def props(sender: ActorRef): Props
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
