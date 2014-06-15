package se.marcuslonnberg.stark.api

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.json4s.Extraction
import se.marcuslonnberg.stark.JsonSupport
import se.marcuslonnberg.stark.api.ProxyApiActor._
import se.marcuslonnberg.stark.proxy.ProxyConf
import spray.http.Uri
import spray.routing.HttpService

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ApiActor {
  def props(proxiesApiActor: ActorRef) = Props(classOf[ApiActor], proxiesApiActor)
}

class ApiActor(proxiesActor: ActorRef) extends Actor with ApiService {
  val proxiesApiActor = context.actorOf(ProxyApiActor.props(proxiesActor), "api")

  override implicit def actorRefFactory = context

  override def receive: Actor.Receive = runRoute(route)

  override implicit def executionContext: ExecutionContext = context.dispatcher
}

trait ApiService extends HttpService with JsonSupport {
  def proxiesApiActor: ActorRef

  implicit def executionContext: ExecutionContext

  implicit val timeout = Timeout(5.seconds)

  def route = pathPrefix("proxies") {
    pathEndOrSingleSlash {
      get {
        complete {
          (proxiesApiActor ? GetProxies).mapTo[Responses.GetProxies].map { response =>
            Extraction.decompose(response.proxies)
          }
        }
      } ~
        (post | put) {
          entity(as[ProxyConf]) { proxy =>
            complete {
              (proxiesApiActor ? AddProxy(proxy)).mapTo[Responses.AddProxy].map { response =>
                Extraction.decompose(response.proxy)
              }
            }
          }
        }
    } ~ path(Rest) { proxyUri =>
      complete {
        val uri = Uri(proxyUri)
        (proxiesApiActor ? GetProxy(uri.authority.host, uri.path)).mapTo[Responses.GetProxy].map { response =>
          Extraction.decompose(response.proxy)
        }
      }
    }
  }
}
