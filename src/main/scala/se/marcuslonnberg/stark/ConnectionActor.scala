package se.marcuslonnberg.stark

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp.ConnectionClosed
import se.marcuslonnberg.stark.auth.AuthActor
import se.marcuslonnberg.stark.auth.AuthActor.{AuthResponse, Authenticated, AuthenticatedHeader, AuthenticatedSession}
import se.marcuslonnberg.stark.proxy.ProxyRequestActor
import se.marcuslonnberg.stark.proxy.ProxyRequestActor.{ProxyRequest, ProxyRequestRouting}
import spray.http.HttpRequest
import spray.http.Uri.Host

object ConnectionActor {
  def props(proxiesActor: ActorRef, apiActor: ActorRef, apiHost: Host) =
    Props(classOf[ConnectionActor], proxiesActor, apiActor, apiHost)
}

class ConnectionActor(proxiesActor: ActorRef, apiActor: ActorRef, apiHost: Host) extends Actor with ActorLogging {
  val proxyActor = context.actorOf(ProxyRequestActor.props(proxiesActor), "proxy")

  val authActor = context.actorOf(AuthActor.props(proxiesActor), "auth")

  log.debug("Connected")

  def receive = initialRequest orElse closeConnection

  def initialRequest: Receive = {
    case request: HttpRequest =>
      authActor ! request
      context.become(checkLogin(request, sender(), None))
  }

  def checkLogin(request: HttpRequest, receiver: ActorRef, handler: Option[ActorRef]): Receive = {
    case AuthResponse(response) =>
      log.debug("Auth response: {}", response)
      receiver ! response
      context.become(initialRequest orElse closeConnection)
    case auth: Authenticated =>
      log.debug("User is authenticated")

      if (request.uri.authority.host == apiHost) {
        apiActor.tell(request, receiver)
      } else {
        auth match {
          case AuthenticatedSession(userInfo, cookie) =>
            proxyActor ! ProxyRequestRouting(ProxyRequest(request, Some(userInfo), cookie), receiver)
          case AuthenticatedHeader =>
            proxyActor ! ProxyRequestRouting(ProxyRequest(request), receiver)
        }
      }

      context.become(keepAlive(proxyActor, receiver) orElse closeConnection)
  }

  def keepAlive(handler: ActorRef, receiver: ActorRef): Receive = {
    case request: HttpRequest =>
      authActor ! request
      context.become(checkLogin(request, sender(), Some(handler)) orElse closeConnection)
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
