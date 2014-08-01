package se.marcuslonnberg.stark

import akka.actor.ActorPath
import org.json4s.{FieldSerializer, CustomSerializer}
import se.marcuslonnberg.stark.site.{Header, Location, ProxyConf}
import spray.http.Uri
import org.json4s.JsonAST._

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

  implicit val actorPathFormat = new CustomSerializer[ActorPath](format => ( {
    case JString(path) => ActorPath.fromString(path)
  }, {
    case path: ActorPath => JString(path.toString)
  }))

  implicit val headerFormat = FieldSerializer[Header]()

  implicit val proxyLocationFormat = new CustomSerializer[Location](format => ( {
    case JString(location) => Location(location)
    case JObject(JField("host", JString(host)) :: JField("path", JString(path)) :: Nil) => Location(host, path)
  }, {
    case location: Location => JString(location.toString)
  }))

  implicit val proxyConfFormat = FieldSerializer[ProxyConf]()
}
