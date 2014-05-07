package se.marcuslonnberg.loginproxy

import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http
import spray.http.Uri
import se.marcuslonnberg.loginproxy.proxy.{ProxyActor, Host, ProxyConf}
import se.marcuslonnberg.loginproxy.auth.AuthActor
import se.marcuslonnberg.loginproxy.proxy.ProxyActor.SetProxies

object LoginProxyApp extends App {
  implicit val system = ActorSystem("proxy")

  val proxy = system.actorOf(ProxyActor.props())
  val auth = system.actorOf(AuthActor.props())

  val connector = system.actorOf(ConnectActor.props(proxy, auth))

  proxy ! SetProxies(List(
    ProxyConf(Host("bbc.local"), Uri("http://www.bbc.co.uk")),
    ProxyConf(Host("cnn.local"), Uri("http://edition.cnn.com"))
  ))

  IO(Http) ! Http.Bind(connector, interface = "localhost", port = 8081)
}
