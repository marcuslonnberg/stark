package se.marcuslonnberg.stark.api

import spray.routing.HttpService
import akka.actor._
import akka.pattern.ask
import scala.concurrent.duration._
import se.marcuslonnberg.stark.Json4sProtocol
import se.marcuslonnberg.stark.api.ProxyApiActor._
import se.marcuslonnberg.stark.proxy.{Host, ProxyConf}
import akka.util.Timeout
import org.json4s.Extraction
import scala.concurrent.ExecutionContext
import spray.http.Uri

object ApiActor {
  def props(proxyApiActor: ActorRef) = Props(classOf[ApiActor], proxyApiActor)
}

class ApiActor(proxyActor: ActorRef) extends Actor with ApiService {
  val proxyApiActor = context.actorOf(ProxyApiActor.props(proxyActor), "api")

  override implicit def actorRefFactory = context

  override def receive: Actor.Receive = runRoute(route)

  override implicit def executionContext: ExecutionContext = context.dispatcher
}

trait ApiService extends HttpService {
  def proxyApiActor: ActorRef

  implicit def executionContext: ExecutionContext

  import Json4sProtocol._

  implicit val timeout = Timeout(5.seconds)

  def route = pathPrefix("proxies") {
    pathEndOrSingleSlash {
      get {
        complete {
          (proxyApiActor ? GetProxies).mapTo[Responses.GetProxies].map{ response =>
            Extraction.decompose(response.proxies)
          }
        }
      } ~
        (post | put) {
          entity(as[ProxyConf]) { proxy =>
            complete {
              (proxyApiActor ? AddProxy(proxy)).mapTo[Responses.AddProxy].map{ response =>
                Extraction.decompose(response.proxy)
              }
            }
          }
        }
    } ~ path(Rest) { proxyUri =>
      complete {
        val host = proxyUri.split("/", 2) match {
          case Array(domain, path) =>
            Host(domain, Some(Uri.Path("/" + path)))
          case _ =>
            Host(proxyUri)
        }
        (proxyApiActor ? GetProxy(host)).mapTo[Responses.GetProxy].map{ response =>
          Extraction.decompose(response.proxy)
        }
      }
    }
  }
}
