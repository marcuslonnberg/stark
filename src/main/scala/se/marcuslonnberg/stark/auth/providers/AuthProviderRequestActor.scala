package se.marcuslonnberg.stark.auth.providers

import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import se.marcuslonnberg.stark.auth.AuthActor.AuthCallback
import se.marcuslonnberg.stark.auth.providers.AuthProvider.AuthResponse
import spray.http.{HttpEntity, HttpResponse, StatusCodes}

import scala.concurrent.duration._

object AuthProviderRequestActor {
  def props(authProvider: AuthProvider, sender: ActorRef) = Props(classOf[AuthProviderRequestActor], authProvider, sender)
}

class AuthProviderRequestActor(authProvider: AuthProvider, sender: ActorRef) extends Actor {

  val provider = context.actorOf(authProvider.props(sender), authProvider.actorName)

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
