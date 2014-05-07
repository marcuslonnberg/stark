package se.marcuslonnberg.loginproxy

import org.json4s.{FieldSerializer, CustomSerializer}
import spray.http.Uri
import org.json4s.JsonAST._
import se.marcuslonnberg.loginproxy.proxy.{Header, ProxyConf}

object JsonFormats {
  implicit val uriFormat = new CustomSerializer[Uri](format => ( {
    case json: JString => Uri(json.s)
  }, {
    case uri: Uri => JString(uri.toString())
  }))

  implicit val listHeaderFormat = new CustomSerializer[List[Header]](format => ( {
    case json: JObject =>
      json.values.map {
        case (key, value: String) =>
          Header(key, value)
        case (key, value) =>
          throw new RuntimeException(s"In key '$key' the value is not a string")
      }.toList
  }, {
    case headers: List[Header] =>
      val map = headers.map(h => h.name -> JString(h.value))
      JObject(map)
  }))

  implicit val proxyFormat = FieldSerializer[ProxyConf]()
}
