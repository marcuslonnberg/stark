package se.marcuslonnberg.loginproxy

import akka.actor.{Props, ActorSystem}
import akka.io.IO
import spray.can.Http
import se.marcuslonnberg.loginproxy.ConnectActor.SetProxies
import spray.http.Uri
import se.marcuslonnberg.loginproxy.proxy.{Host, ProxyConf}

object LoginProxyApp extends App {
  implicit val system = ActorSystem("proxy")

  val proxy = system.actorOf(Props(classOf[ConnectActor]))

  proxy ! SetProxies(List(
    ProxyConf(Host("bbc.local"), Uri("http://www.bbc.co.uk")),
    ProxyConf(Host("cnn.local"), Uri("http://edition.cnn.com"))
  ))

  IO(Http) ! Http.Bind(proxy, interface = "localhost", port = 8081)
}
