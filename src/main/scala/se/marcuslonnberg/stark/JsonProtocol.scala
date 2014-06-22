package se.marcuslonnberg.stark

import spray.httpx.Json4sSupport
import org.json4s._

trait JsonSupport extends Json4sSupport {
  implicit def json4sFormats: Formats = DefaultFormats +
    JsonFormats.headerFormat +
    JsonFormats.uriFormat +
    JsonFormats.uriHostFormat +
    JsonFormats.uriPathFormat +
    JsonFormats.proxyLocationFormat +
    JsonFormats.proxyFormat
}

object JsonProtocol extends JsonSupport
