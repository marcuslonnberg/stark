package se.marcuslonnberg.stark.proxy

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import se.marcuslonnberg.stark.proxy.ProxiesActor.{NoProxyConfFound, ProxyFor, ProxyRequest}
import spray.http.{HttpResponse, StatusCodes}

object ProxyRequestActor {
  def props(proxiesRef: ActorRef) = Props(classOf[ProxyRequestActor], proxiesRef)
}

class ProxyRequestActor(proxiesRef: ActorRef) extends Actor with ActorLogging {
  def receive = receiveRequest

  var connections = Map.empty[ProxyConf, ActorRef]

  def receiveRequest: Receive = {
    case (proxyRequest@ProxyRequest(request, userInfo, cookie), receiver: ActorRef) =>
      log.debug("Requesting proxy for {}", request.uri)
      proxiesRef ! ProxyFor(request.uri)
      context.become(waitForProxyConf(proxyRequest, receiver))
  }

  def waitForProxyConf(proxyRequest: ProxyRequest, receiver: ActorRef): Receive = {
    case proxy: ProxyConf =>
      log.debug("Found proxy: {}", proxy)

      val connection = connections.getOrElse(proxy, {
        val c = context.actorOf(ProxyConnectionActor.props(proxy))
        connections += (proxy -> c)
        c
      })

      connection ! (proxyRequest -> receiver)
      context.become(receiveRequest)
    case NoProxyConfFound =>
      log.debug("Could not find any proxy")
      receiver ! HttpResponse(StatusCodes.NotFound, s"No proxy for ${proxyRequest.request.uri}")
      context.become(receiveRequest)
  }
}
