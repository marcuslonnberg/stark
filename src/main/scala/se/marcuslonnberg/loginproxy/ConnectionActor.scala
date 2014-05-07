package se.marcuslonnberg.loginproxy

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import spray.http.HttpRequest

object ConnectionActor {
  def props(proxyActor: ActorRef, authActor: ActorRef) = Props(classOf[ConnectionActor], proxyActor, authActor)
}

class ConnectionActor(proxyActor: ActorRef, authActor: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case request: HttpRequest =>
      context.actorOf(RequestActor.props(request, sender(), proxyActor, authActor))
  }
}
