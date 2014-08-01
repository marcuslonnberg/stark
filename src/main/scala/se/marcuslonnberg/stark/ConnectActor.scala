package se.marcuslonnberg.stark

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import spray.can.Http
import spray.http.Uri.Host

object ConnectActor {
  def props(sitesActor: ActorRef) =
    Props(classOf[ConnectActor], sitesActor)
}

class ConnectActor(sitesActor: ActorRef) extends Actor with ActorLogging {
  override def receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      log.info("Connection established from {} to {}", remoteAddress, localAddress)
      val connection = context.actorOf(ConnectionActor.props(sitesActor))
      sender ! Http.Register(connection)
  }
}
