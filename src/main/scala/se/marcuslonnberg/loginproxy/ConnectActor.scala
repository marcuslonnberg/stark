package se.marcuslonnberg.loginproxy

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import spray.can.Http

object ConnectActor {
  def props(proxyActor: ActorRef, authActor: ActorRef) = Props(classOf[ConnectActor], proxyActor, authActor)
}

class ConnectActor(proxyActor: ActorRef, authActor: ActorRef) extends Actor with ActorLogging {
  override def receive = {
    case _: Http.Connected =>
      val proxy = context.actorOf(ConnectionActor.props(proxyActor, authActor))
      sender ! Http.Register(proxy)
  }
}
