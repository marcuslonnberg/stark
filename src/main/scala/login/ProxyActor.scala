package login

import akka.actor.{Props, ActorRef, Actor}
import spray.http._
import akka.io.IO
import spray.can.Http
import spray.http.HttpRequest

case class Destination(host: String, port: Int = 80)

object ProxyActor {
  def props(host: Destination) = Props(classOf[ProxyActor], host)
}

class ProxyActor(destination: Destination) extends Actor {
  import context._

  val io = IO(Http)

  override def receive = {
    case request: HttpRequest =>
      val origSender = sender

      val uri = request.uri
        .withHost(destination.host)
        .withPort(destination.port)

      val headers = request.headers.map {
        case _: HttpHeaders.Host => HttpHeaders.Host(destination.host)
        case other => other
      }
      val proxiedRequest = request.copy(uri = uri).withHeaders(headers)

      io ! proxiedRequest

      context.become(response(origSender))
  }

  def response(receiver: ActorRef): Receive = {
    case response: HttpResponse =>
      receiver ! response
  }
}
