package se.marcuslonnberg.stark

import akka.actor.ActorSystem
import akka.io.IO
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import se.marcuslonnberg.stark.api.ApiActor
import se.marcuslonnberg.stark.api.ProxyApiActor.AddProxy
import se.marcuslonnberg.stark.proxy.{ProxiesActor, ProxyConf}
import spray.can.Http
import spray.can.server.ServerSettings
import spray.http.Uri
import spray.http.Uri.Host

import scala.collection.convert.wrapAsScala._
import scala.concurrent.duration._

object StarkProxyApp extends App with SSLSupport {
  implicit val system = ActorSystem("proxy")
  sys.addShutdownHook(system.shutdown())

  val conf = ConfigFactory.load()

  val apiHost = Uri.Host(conf.as[String]("server.apiHost"))
  val proxies = system.actorOf(ProxiesActor.props(apiHost), "proxy")
  val apiRoutingActor = system.actorOf(ApiActor.props(proxies), "api-routing")
  val connector = system.actorOf(ConnectActor.props(proxies, apiRoutingActor, apiHost), "connector")

  import system.dispatcher

  system.scheduler.scheduleOnce(1.seconds) {
    val proxyApi = system.actorSelection(apiRoutingActor.path / "api")
    proxyApi ! AddProxy(ProxyConf(Host("bbc.local"), upstream = Uri("http://www.bbc.co.uk")))
    proxyApi ! AddProxy(ProxyConf(Host("cnn.local"), upstream = Uri("http://edition.cnn.com")))
  }

  private val bindings = conf.getObjectList("server.bindings")
  println(s"Found ${bindings.length} bindings in config")
  bindings.foreach(b => bind(b.toConfig))

  def bind(bindConfig: Config) = {
    val bindInterface = bindConfig.getAs[String]("interface").getOrElse(conf.as[String]("server.interface"))
    val port = bindConfig.as[Int]("port")
    val ssl = bindConfig.getAs[Boolean]("ssl").getOrElse(false)

    val bind = {
      val bind = Http.Bind(connector, interface = bindInterface, port = port)
      if (ssl) {
        val certFile = bindConfig.as[String]("cert-file")
        val privateKeyFile = bindConfig.as[String]("private-key-file")
        implicit val sslContext = createSSLContext(certFile, privateKeyFile)

        bind.copy(settings = Some(ServerSettings(conf).copy(sslEncryption = ssl)))
      } else {
        bind
      }
    }

    IO(Http) ! bind
  }
}
