package se.marcuslonnberg.stark

import akka.actor.ActorSystem
import akka.io.IO
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import se.marcuslonnberg.stark.api.ApiActor
import se.marcuslonnberg.stark.proxy.ProxiesActor
import spray.can.Http
import spray.can.server.ServerSettings
import spray.http.Uri

import scala.collection.convert.wrapAsScala._

object StarkProxyApp extends App with SSLSupport {
  implicit val system = ActorSystem("proxy")
  sys.addShutdownHook(system.shutdown())

  val conf = ConfigFactory.load()

  val proxies = system.actorOf(ProxiesActor.props(), "proxies")
  val apiRoutingActor = system.actorOf(ApiActor.props(proxies), "api-routing")

  val apiHost = Uri.Host(conf.as[String]("server.api-host"))
  val connector = system.actorOf(ConnectActor.props(proxies, apiRoutingActor, apiHost), "connector")

  val bindings = conf.getObjectList("server.bindings")
  println(s"Found ${bindings.length} bindings in config")
  bindings.foreach(b => bind(b.toConfig))

  def bind(bindConfig: Config) = {
    val bindInterface = bindConfig.getAs[String]("interface").getOrElse(conf.as[String]("server.default-interface"))
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
