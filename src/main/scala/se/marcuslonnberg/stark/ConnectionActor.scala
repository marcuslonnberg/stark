package se.marcuslonnberg.stark

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import se.marcuslonnberg.stark.auth.AuthActor.{AuthResponse, LoggedIn}
import se.marcuslonnberg.stark.proxy.ProxiesActor.ProxyRequest
import se.marcuslonnberg.stark.proxy.ProxyRequestActor
import spray.http.HttpRequest
import spray.http.Uri.Host

object ConnectionActor {
  def props(proxiesActor: ActorRef, authActor: ActorRef, apiActor: ActorRef, apiHost: Host) =
    Props(classOf[ConnectionActor], proxiesActor, authActor, apiActor, apiHost)
}

class ConnectionActor(proxiesActor: ActorRef, authActor: ActorRef, apiActor: ActorRef, apiHost: Host) extends Actor with ActorLogging {
  val proxyActor = context.actorOf(ProxyRequestActor.props(proxiesActor))

  def receive = initialRequest

  def initialRequest: Receive = {
    case request: HttpRequest =>
      authActor ! request
      context.become(checkLogin(request, sender(), None))
  }

  def checkLogin(request: HttpRequest, receiver: ActorRef, handler: Option[ActorRef]): Receive = {
    case AuthResponse(response) =>
      log.debug("Auth response")
      receiver ! response
      context.become(initialRequest)
    case LoggedIn(userInfo, cookie) =>
      log.debug("User logged in")

      if (request.uri.authority.host == apiHost) {
        apiActor.tell(request, receiver)
      } else {
        proxyActor ! (ProxyRequest(request, userInfo, cookie) -> receiver)
      }

      context.become(keepAlive(proxyActor, receiver))
    case x =>
      log.debug("Other: {}", x)
  }

  def keepAlive(handler: ActorRef, receiver: ActorRef): Receive = {
    case request: HttpRequest =>
      authActor ! request
      context.become(checkLogin(request, sender(), Some(handler)))
    case x =>
      log.debug("Other: {}", x)
  }
}
