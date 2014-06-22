package se.marcuslonnberg.stark

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import spray.can.Http
import spray.http.Uri.Host

object ConnectActor {
  def props(proxiesActor: ActorRef, apiActor: ActorRef, apiHost: Host) =
    Props(classOf[ConnectActor], proxiesActor, apiActor, apiHost)
}

class ConnectActor(proxiesActor: ActorRef, apiActor: ActorRef, apiHost: Host) extends Actor with ActorLogging {
  override def receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      log.info("Connection established from {} to {}", remoteAddress, localAddress)
      val proxy = context.actorOf(ConnectionActor.props(proxiesActor, apiActor, apiHost))
      sender ! Http.Register(proxy)
  }
}
