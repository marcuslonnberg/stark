package se.marcuslonnberg.stark.utils

import spray.http.{ContentTypes, StatusCode, HttpEntity, StatusCodes}
import spray.routing.Directives._
import spray.routing.StandardRoute

object Directives {
  def forbiddenWhen(enabled: Boolean)(message: => String): StandardRoute = {
    if (enabled) {
      complete(StatusCodes.Forbidden, HttpEntity(message))
    } else {
      reject
    }
  }
}
