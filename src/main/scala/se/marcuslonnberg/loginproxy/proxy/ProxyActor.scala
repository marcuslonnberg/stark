package se.marcuslonnberg.loginproxy.proxy

import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import spray.http._
import akka.io.IO
import spray.can.Http
import spray.http.HttpRequest
import se.marcuslonnberg.loginproxy.proxy.ProxyActor.{SetProxies, ProxyRequest}
import spray.http.HttpResponse
import spray.http.HttpHeaders.RawHeader
import se.marcuslonnberg.loginproxy.auth.AuthActor.UserInfo

object ProxyActor {

  def props() = Props(classOf[ProxyActor])

  case class ProxyRequest(request: HttpRequest, userInfo: UserInfo, cookie: Option[HttpCookie])

  case class SetProxies(proxies: List[ProxyConf])

}

class ProxyActor extends Actor with ActorLogging {
  override def receive: Receive = state(List.empty)

  def state(proxies: List[ProxyConf]): Receive = {
    case request: ProxyRequest =>
      context.actorOf(ProxyRequestActor.props(request, proxies, sender()))
    case SetProxies(newProxies) =>
      context.become(state(newProxies))
  }
}

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
    val proxyPath = conf.host.path.map(path => request.uri.path.dropChars(path.length)).getOrElse(request.uri.path)
    val uri = conf.upstream.withPath(proxyPath)

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
