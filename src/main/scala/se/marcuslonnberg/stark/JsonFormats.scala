package se.marcuslonnberg.stark

import org.json4s.{FieldSerializer, CustomSerializer}
import spray.http.Uri
import org.json4s.JsonAST._
import se.marcuslonnberg.stark.proxy.{ProxyLocation, Header, ProxyConf}

object JsonFormats {
  implicit val uriFormat = new CustomSerializer[Uri](format => ( {
    case JString(uri) => Uri(uri)
  }, {
    case uri: Uri => JString(uri.toString())
  }))

  implicit val uriHostFormat = new CustomSerializer[Uri.Host](format => ( {
    case JString(host) => Uri.Host(host)
  }, {
    case host: Uri.Host => JString(host.address)
  }))

  implicit val uriPathFormat = new CustomSerializer[Uri.Path](format => ( {
    case JString(path) => Uri.Path(path)
  }, {
    case path: Uri.Path => JString(path.toString())
  }))

  implicit val headerFormat = FieldSerializer[Header]()

  implicit val proxyLocationFormat = new CustomSerializer[ProxyLocation](format => ( {
    case JString(location) => ProxyLocation(location)
    case JObject(JField("host", JString(host)) :: JField("path", JString(path)) :: Nil) => ProxyLocation(host, path)
  }, {
    case location: ProxyLocation => JString(location.toString)
  }))

  implicit val proxyFormat = FieldSerializer[ProxyConf]()
}
