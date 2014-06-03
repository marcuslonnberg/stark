package se.marcuslonnberg.stark

import akka.actor.{Props, ActorRef, Actor}
import spray.http.HttpRequest
import se.marcuslonnberg.stark.auth.AuthActor.{AuthResponse, LoggedIn}
import se.marcuslonnberg.stark.proxy.ProxyActor.ProxyRequest

object RequestActor {

  def props(request: HttpRequest, receiver: ActorRef, proxyActor: ActorRef, authActor: ActorRef) =
    Props(classOf[RequestActor], request, receiver, proxyActor, authActor)

}

class RequestActor(request: HttpRequest, receiver: ActorRef, proxyActor: ActorRef, authActor: ActorRef) extends Actor {
  authActor ! request

  override def receive: Receive = checkLogin

  def checkLogin: Receive = {
    case AuthResponse(response) =>
      receiver ! response
      context.stop(self)
    case LoggedIn(userInfo, cookie) =>
      proxyActor.tell(ProxyRequest(request, userInfo, cookie), receiver)
      context.stop(self)
  }
}
