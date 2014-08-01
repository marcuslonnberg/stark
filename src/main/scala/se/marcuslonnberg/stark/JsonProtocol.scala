package se.marcuslonnberg.stark

import org.json4s._
import spray.httpx.Json4sSupport

trait JsonSupport extends Json4sSupport {
  implicit def json4sFormats: Formats = DefaultFormats +
    JsonFormats.headerFormat +
    JsonFormats.uriFormat +
    JsonFormats.uriHostFormat +
    JsonFormats.uriPathFormat +
    JsonFormats.actorPathFormat +
    JsonFormats.proxyLocationFormat +
    JsonFormats.proxyConfFormat
}

object JsonProtocol extends JsonSupport
