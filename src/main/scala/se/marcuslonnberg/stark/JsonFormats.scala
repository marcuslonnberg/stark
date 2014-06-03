package se.marcuslonnberg.stark

import org.json4s.{FieldSerializer, CustomSerializer}
import spray.http.Uri
import org.json4s.JsonAST._
import se.marcuslonnberg.stark.proxy.{Header, ProxyConf}

object JsonFormats {
  implicit val uriFormat = new CustomSerializer[Uri](format => ( {
    case json: JString => Uri(json.s)
  }, {
    case uri: Uri => JString(uri.toString())
  }))

  implicit val uriPathFormat = new CustomSerializer[Uri.Path](format => ( {
    case json: JString => Uri.Path(json.s)
  }, {
    case path: Uri.Path => JString(path.toString())
  }))

  implicit val headerFormat = FieldSerializer[Header]()

  implicit val proxyFormat = FieldSerializer[ProxyConf]()
}
