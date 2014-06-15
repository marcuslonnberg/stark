package se.marcuslonnberg.stark.auth

import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import se.marcuslonnberg.stark.auth.AuthActor.AuthCallback
import se.marcuslonnberg.stark.auth.AuthProvider.AuthResponse
import spray.http.{HttpEntity, StatusCodes, HttpResponse}
import scala.concurrent.duration._

object AuthRequestActor {
  def props(authProvider: AuthProvider, sender: ActorRef) = Props(classOf[AuthRequestActor], authProvider, sender)
}

class AuthRequestActor(authProvider: AuthProvider, sender: ActorRef) extends Actor {

  val provider = context.actorOf(authProvider.props(sender))

  context.setReceiveTimeout(30.seconds)

  def receive = {
    case response: AuthResponse =>
      provider ! response
    case callback: AuthCallback =>
      context.parent.tell(callback, sender)
      context.stop(self)
    case _: ReceiveTimeout =>
      sender ! HttpResponse(StatusCodes.GatewayTimeout, HttpEntity("Authentication timed out"))
      context.stop(self)
  }

  override def supervisorStrategy = OneForOneStrategy() {
    case ex =>
      sender ! HttpResponse(StatusCodes.InternalServerError, HttpEntity("Error when authenticating"))
      context.stop(self)
      Stop
  }
}
