package se.marcuslonnberg.stark

import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http
import spray.http.Uri
import se.marcuslonnberg.stark.proxy.{ProxyActor, Host, ProxyConf}
import se.marcuslonnberg.stark.auth.AuthActor
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.FicusConfig._
import se.marcuslonnberg.stark.api.ProxyApiActor.AddProxy
import scala.concurrent.duration._
import spray.can.server.ServerSettings
import scala.collection.convert.wrapAsScala._

object StarkProxyApp extends App with SSLSupport {
  implicit val system = ActorSystem("proxy")
  sys.addShutdownHook(system.shutdown())

  val conf = ConfigFactory.load()

  val apiHost = Uri.Host(conf.as[String]("server.apiHost"))
  val proxy = system.actorOf(ProxyActor.props(apiHost), "proxy")
  val auth = system.actorOf(AuthActor.props(), "auth")

  val connector = system.actorOf(ConnectActor.props(proxy, auth), "connector")

  import system.dispatcher

  system.scheduler.scheduleOnce(1.seconds) {
    val proxyApi = system.actorSelection(proxy.path / "api-routing" / "api")
    proxyApi ! AddProxy(ProxyConf(Host("bbc.local"), Uri("http://www.bbc.co.uk")))
    proxyApi ! AddProxy(ProxyConf(Host("cnn.local"), Uri("http://edition.cnn.com")))
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

        bind.copy(settings = Some(ServerSettings(system).copy(sslEncryption = ssl)))
      } else {
        bind
      }
    }

    IO(Http) ! bind
  }
}
