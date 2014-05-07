package se.marcuslonnberg.loginproxy.proxy

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import akka.io.IO
import spray.can.Http
import spray.http._
import spray.http.HttpRequest
import spray.http.HttpResponse
import scala.Some
import spray.http.HttpHeaders.RawHeader

object ProxyActor {
  def props(proxies: List[ProxyConf]) = Props(classOf[ProxyActor], proxies)
}

class ProxyActor(proxies: List[ProxyConf]) extends Actor with ActorLogging {

  import context._

  val io = IO(Http)

  def getProxy(requestUri: Uri): Option[ProxyConf] = {
    val requestAddress = requestUri.authority.host.address
    val requestPath = requestUri.path
    proxies.find { proxy =>
      val path = proxy.host.path.isEmpty || proxy.host.path.exists(path => requestPath.startsWith(path))
      val address = proxy.host.address == requestAddress
      address && path
    }
  }

  def transformRequest(request: HttpRequest, conf: ProxyConf): HttpRequest = {
    val proxyPath = conf.host.path.map(path => request.uri.path.dropChars(path.length)).getOrElse(request.uri.path)
    val uri = conf.upstream.withPath(proxyPath)

    val newHeaders = conf.headers.map(header => RawHeader(header.name, header.value))

    val headers = request.headers.map {
      case _: HttpHeaders.Host => HttpHeaders.Host(conf.upstream.authority.host.address, conf.upstream.authority.port)
      case other => other
    } ++ newHeaders

    request.copy(uri = uri).withHeaders(headers)
  }

  override def receive: Receive = {
    case request: HttpRequest =>
      getProxy(request.uri) match {
        case Some(proxy) =>
          log.debug("Found proxy {}", proxy)

          val proxiedRequest = transformRequest(request, proxy)
          io ! proxiedRequest

          context.become(response(sender), discardOld = false)
        case None =>
          sender ! HttpResponse(StatusCodes.NotFound, s"No proxy for ${request.uri}")
      }
  }

  def response(receiver: ActorRef): Receive = {
    case response: HttpResponse =>
      receiver ! response
      context.unbecome()
  }

  override def unhandled(message: Any) = {
    log.warning("Unhandled message: {}", message)
  }
}
