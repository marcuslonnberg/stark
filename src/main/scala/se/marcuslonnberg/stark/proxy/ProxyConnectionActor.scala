package se.marcuslonnberg.stark.proxy

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.IO
import akka.io.Tcp.{Connected, ConnectionClosed}
import com.typesafe.config.ConfigFactory
import se.marcuslonnberg.stark.proxy.ProxyRequestActor.{ProxyRequest, SiteRequestRouting}
import se.marcuslonnberg.stark.site.Implicits._
import se.marcuslonnberg.stark.site.ProxyConf
import spray.can.Http
import spray.can.client.ClientConnectionSettings
import spray.http.HttpHeaders.RawHeader
import spray.http._

object ProxyConnectionActor {
  def props(proxy: ProxyConf) = Props(classOf[ProxyConnectionActor], proxy)
}

class ProxyConnectionActor(proxy: ProxyConf) extends Actor with ActorLogging with RequestTransformer {
  val config = ConfigFactory.load()
  val authCookieName = config.getString("auth.cookie-name")
  val authHeaderName = config.getString("auth.header-name")

  import context._

  val io = IO(Http)

  def receive = firstRequest

  def firstRequest: Receive = {
    case rr: SiteRequestRouting =>
      log.debug("Connecting")
      val transformedRequest = transformRequest(rr.request, proxy)
      val requestUri = transformedRequest.uri
      log.debug("Transformed request URI: {}", transformedRequest.uri)

      val settings = ClientConnectionSettings(system).copy(chunklessStreaming = false)
      val port = requestUri.effectivePort

      io ! Http.Connect(requestUri.authority.host.address, port,
        sslEncryption = requestUri.scheme == "https",
        settings = Some(settings))
      context.become(connecting(transformedRequest, rr.receiver))
  }

  def connecting(transformedRequest: HttpRequest, receiver: ActorRef): Receive = {
    case Connected(_, _) =>
      log.debug("Connected")
      io ! transformedRequest
      context.become(response(receiver))
    case x =>
      log.debug("Other: {}", x)
  }

  def response(receiver: ActorRef): Receive = {
    case response: ChunkedResponseStart =>
      log.debug("Received start of chunked response")
      receiver ! response.copy(response.response.copy(protocol = HttpProtocols.`HTTP/1.1`))
    case message: MessageChunk =>
      receiver ! message
    case message: ChunkedMessageEnd =>
      log.debug("Received end of chunked response")
      receiver ! message
      context.become(request)
    case response: HttpResponse =>
      log.debug("Received response with size {} bytes", response.entity.data.length)
      receiver ! response.copy(protocol = HttpProtocols.`HTTP/1.1`)
      context.become(request)
    case close: ConnectionClosed =>
      log.debug("Close")
      context.become(firstRequest)
  }

  def request: Receive = {
    case SiteRequestRouting(request, receiver) =>
      val transformedRequest = transformRequest(request, proxy)
      log.debug("Transformed request URI: {}", transformedRequest.uri)

      io ! transformedRequest
      context.become(response(receiver))
  }

  override def unhandled(message: Any) = {
    log.warning("Unhandled: {}", message)
  }
}

trait RequestTransformer {
  def authCookieName: String

  def authHeaderName: String

  def transformRequest(proxyRequest: ProxyRequest, conf: ProxyConf): HttpRequest = {
    val requestUri = proxyRequest.request.uri
    val proxyPath = requestUri.path.relativizeTo(conf.location.path).get
    val upstreamPath = {
      val p = conf.upstream.path
      // Remove ending slash
      if (p.reverse.startsWithSlash) p.reverse.tail.reverse
      else p
    }
    val uri = conf.upstream
      .withPath(upstreamPath ++ proxyPath)
      .withQuery(requestUri.query)
      .withFragment(requestUri.fragment.getOrElse(""))

    val newHeaders = conf.headers.map(header => RawHeader(header.name, header.value))

    val authHeaderNameLower = authHeaderName.toLowerCase
    val headers = proxyRequest.request.headers.withFilter(_.isNot(authHeaderNameLower)).map {
      case _: HttpHeaders.Host => HttpHeaders.Host(conf.upstream.authority.host.address, conf.upstream.authority.port)
      case cookie: HttpHeaders.Cookie => HttpHeaders.Cookie(cookie.cookies.filter(_.name != authCookieName))
      case other => other
    } ++ newHeaders

    proxyRequest.request.copy(uri = uri).withHeaders(headers)
  }
}
