package se.marcuslonnberg.stark.site

import akka.actor.ActorPath
import org.json4s.JsonAST.JObject
import spray.http.Uri

object Location {
  def apply(location: String): Location = {
    val `//location` = if (location.contains("//")) location else "//" + location
    val uri = Uri(`//location`)

    assert(uri.scheme.isEmpty, "Expected a URI without a scheme")
    assert(uri.authority.userinfo.isEmpty, "Expected a URI without user information")
    assert(uri.authority.port == 0, "Expected a URI without a port")
    assert(uri.query.isEmpty, "Expected a URI without a query")
    assert(uri.fragment.isEmpty, "Expected a URI without a fragment")
    assert(uri.authority.host.address.nonEmpty, "Expected a URI with a host")

    Location(uri.authority.host, uri.path)
  }

  def apply(host: String, path: String): Location = {
    Location(Uri.Host(host), Uri.Path(path))
  }
}

case class Location(host: Uri.Host,
                    path: Uri.Path = Uri.Path.Empty) {
  override def toString = host.address + path.toString()
}

trait Site {
  def location: Location
}

case class ProxyConf(location: Location,
                     upstream: Uri,
                     headers: List[Header] = List(),
                     metadata: Option[JObject] = None) extends Site

case class Header(name: String, value: String)

trait ActorSite extends Site {
  def recipient: ActorPath
}

case class ApiConf(location: Location, recipient: ActorPath) extends ActorSite
