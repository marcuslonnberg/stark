package login

import spray.can.Http
import akka.actor.Actor

class ConnectActor extends Actor {
  val destination = Destination("www.bbc.co.uk")

  override def receive = {
    case x: Http.Connected =>
      val proxy = context.actorOf(ProxyActor.props(destination))
      sender ! Http.Register(proxy)
  }
}
