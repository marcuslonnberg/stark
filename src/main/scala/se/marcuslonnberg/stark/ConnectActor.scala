package se.marcuslonnberg.stark

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import spray.can.Http
import spray.http.Uri.Host

object ConnectActor {
  def props(proxiesActor: ActorRef, authActor: ActorRef, apiActor: ActorRef, apiHost: Host) =
    Props(classOf[ConnectActor], proxiesActor, authActor, apiActor, apiHost)
}

class ConnectActor(proxiesActor: ActorRef, authActor: ActorRef, apiActor: ActorRef, apiHost: Host) extends Actor with ActorLogging {
  override def receive = {
    case _: Http.Connected =>
      val proxy = context.actorOf(ConnectionActor.props(proxiesActor, authActor, apiActor, apiHost))
      sender ! Http.Register(proxy)
  }
}
