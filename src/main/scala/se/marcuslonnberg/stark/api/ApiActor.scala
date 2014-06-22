package se.marcuslonnberg.stark.api

import akka.actor.{Actor, Props, ActorRef}
import com.typesafe.config.ConfigFactory
import se.marcuslonnberg.stark.auth.storage.RedisAuthStore
import net.ceedubs.ficus.Ficus._
import scala.concurrent.ExecutionContext

object ApiActor {
  def props(proxiesApiActor: ActorRef) = Props(classOf[ApiActor], proxiesApiActor)
}

class ApiActor(val proxiesActor: ActorRef) extends Actor with ProxiesApiService with AuthHeadersApiService with AuthSessionsApiService {
  override implicit def actorRefFactory = context

  override def receive: Actor.Receive = runRoute(proxiesRoute ~ sessionsRoute ~ headersRoute)

  override implicit def executionContext: ExecutionContext = context.dispatcher

  override val redis = new RedisAuthStore {
    implicit def system = context.system
  }

  val config = ConfigFactory.load().getConfig("api")

  val proxiesEnabled = config.as[Boolean]("proxies")

  val sessionsEnabled = config.as[Boolean]("sessions")

  val headersEnabled = config.as[Boolean]("headers")
}
