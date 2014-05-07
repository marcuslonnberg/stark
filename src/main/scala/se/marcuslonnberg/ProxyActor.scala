package se.marcuslonnberg

import akka.actor.{ActorLogging, Props, ActorRef, Actor}
import spray.http._
import akka.io.IO
import spray.can.Http
import spray.http.HttpRequest

case class Destination(host: String, port: Int = 0)

object ProxyActor {
  def props(hosts: Map[String, Destination]) = Props(classOf[ProxyActor], hosts)
}

class ProxyActor(hosts: Map[String, Destination]) extends Actor with ActorLogging {

  import context._

  val io = IO(Http)

  override def receive = {
    case request: HttpRequest =>
      val address = request.uri.authority.host.address
      hosts.get(address) match {
        case Some(destination) =>
          val uri = request.uri
            .withHost(destination.host)
            .withPort(destination.port)

          val headers = request.headers.map {
            case _: HttpHeaders.Host => HttpHeaders.Host(destination.host, destination.port)
            case other => other
          }
          val proxiedRequest = request.copy(uri = uri).withHeaders(headers)

          io ! proxiedRequest

          context.become(response(sender), discardOld = false)
        case None =>
          sender ! HttpResponse(StatusCodes.NotFound, s"No proxy for $address")
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
