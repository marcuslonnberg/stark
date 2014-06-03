package se.marcuslonnberg.stark.proxy

import akka.actor.{ActorLogging, Actor, Props, ActorRef}
import akka.io.IO
import spray.can.Http
import spray.http._
import spray.http.HttpRequest
import spray.http.HttpHeaders.RawHeader
import scala.Some
import spray.http.HttpResponse
import se.marcuslonnberg.stark.proxy.ProxyActor.ProxyRequest

object ProxyRequestActor {
  def props(proxyRequest: ProxyRequest, proxies: List[ProxyConf], receiver: ActorRef) =
    Props(classOf[ProxyRequestActor], proxyRequest, proxies, receiver)
}

class ProxyRequestActor(proxyRequest: ProxyRequest, proxies: List[ProxyConf], receiver: ActorRef) extends Actor with ActorLogging {

  import context._

  val io = IO(Http)

  override def preStart() = {
    val requestUri = proxyRequest.request.uri
    getProxy(requestUri) match {
      case Some(proxy) =>
        log.debug("Found proxy {} for URI '{}'", proxy, requestUri)

        // TODO: remove login cookie from proxied request
        val proxiedRequest = transformRequest(proxy)
        io ! proxiedRequest
      case None =>
        val notFound = HttpResponse(StatusCodes.NotFound, s"No proxy for $requestUri")
        val notFoundWithCookie = addCookie(notFound, proxyRequest.cookie)
        receiver ! notFoundWithCookie
    }
  }

  override def receive: Actor.Receive = {
    case response: HttpResponse =>
      val updatedResponse = addCookie(response, proxyRequest.cookie)
      receiver ! updatedResponse
      context.stop(self)
  }

  def transformRequest(conf: ProxyConf): HttpRequest = {
    val request = proxyRequest.request
    val proxyPath = conf.host.path.map(path => request.uri.path.dropChars(path.toString().length)).getOrElse(request.uri.path)
    val uri = conf.upstream.withPath(conf.upstream.path ++ proxyPath)

    val newHeaders = conf.headers.map(header => RawHeader(header.name, header.value))

    val headers = request.headers.map {
      case _: HttpHeaders.Host => HttpHeaders.Host(conf.upstream.authority.host.address, conf.upstream.authority.port)
      case other => other
    } ++ newHeaders

    request.copy(uri = uri).withHeaders(headers)
  }

  def getProxy(requestUri: Uri): Option[ProxyConf] = {
    val requestAddress = requestUri.authority.host.address
    val requestPath = requestUri.path
    proxies.find { proxy =>
      val path = proxy.host.path.isEmpty || proxy.host.path.exists(path => requestPath.startsWith(path))
      val address = proxy.host.address == requestAddress
      address && path
    }
  }

  def addCookie(response: HttpResponse, cookieOption: Option[HttpCookie]) = {
    cookieOption match {
      case Some(cookie) => response.copy(headers = HttpHeaders.`Set-Cookie`(cookie) :: response.headers)
      case None => response
    }
  }
}
