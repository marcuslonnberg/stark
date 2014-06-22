package se.marcuslonnberg.stark.proxy

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.io.IO
import akka.io.Tcp.{ConnectionClosed, Connected}
import com.typesafe.config.ConfigFactory
import se.marcuslonnberg.stark.proxy.ProxyRequestActor.{ProxyRequest, ProxyRequestRouting}
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
    case rr: ProxyRequestRouting =>
      log.debug("Connecting")
      val transformedRequest = transformRequest(rr.request, proxy)
      val requestUri = transformedRequest.uri

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
    case ProxyRequestRouting(request, receiver) =>
      val transformedRequest = transformRequest(request, proxy)

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
    val proxyPath = requestUri.path.dropChars(conf.location.path.length)
    val uri = conf.upstream.withPath(conf.upstream.path ++ proxyPath)

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
