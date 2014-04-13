package se.marcuslonnberg

import akka.actor.{Props, ActorSystem}
import akka.io.IO
import spray.can.Http

object ProxyApp extends App {
  implicit val system = ActorSystem("proxy")

  val proxy = system.actorOf(Props(classOf[ConnectActor]))
  IO(Http) ! Http.Bind(proxy, interface = "localhost", port = 8081)
}
