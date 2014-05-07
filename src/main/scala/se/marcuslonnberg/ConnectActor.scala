package se.marcuslonnberg

import spray.can.Http
import akka.actor.Actor

class ConnectActor extends Actor {
  val destinations = Map(
    "bbc.local" -> Destination("www.bbc.co.uk"),
    "cnn.local" -> Destination("edition.cnn.com")
  )

  override def receive = {
    case x: Http.Connected =>
      val proxy = context.actorOf(ProxyActor.props(destinations))
      sender ! Http.Register(proxy)
  }
}
