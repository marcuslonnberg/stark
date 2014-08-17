package se.marcuslonnberg.stark.static

import akka.actor.{Actor, Props}
import se.marcuslonnberg.stark.ConnectionActor.SiteRequest
import se.marcuslonnberg.stark.site.StaticContentConf
import spray.routing.HttpService

object StaticContentActor {
  def props(conf: StaticContentConf) = Props(classOf[StaticContentActor], conf)
}

class StaticContentActor(conf: StaticContentConf) extends Actor with HttpService {
  implicit def actorRefFactory = context

  val fallbackFilename = "index.html"

  def receive = receiveSiteRequest orElse runRoute(route)
  
  def receiveSiteRequest: Receive = {
    case siteRequest: SiteRequest =>
      self.tell(siteRequest.requestRelativePath, siteRequest.receiver)
  }

  val route = get {
    getFromDirectory(conf.path) ~ { ctx =>
      val updatedCtx = ctx.withUnmatchedPathMapped { path =>
        if (path.reverse.startsWithSlash) path + fallbackFilename
        else path / fallbackFilename
      }

      getFromDirectory(conf.path).apply(updatedCtx)
    }
  }
}
