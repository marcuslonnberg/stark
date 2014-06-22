package se.marcuslonnberg.stark.proxy

import spray.http.Uri
import org.json4s.JsonAST.JObject

object ProxyLocation {
  def apply(location: String): ProxyLocation = {
    val `//location` = if (location.contains("//")) location else "//" + location
    val uri = Uri(`//location`)

    assert(uri.scheme.isEmpty, "Expected a URI without a scheme")
    assert(uri.authority.userinfo.isEmpty, "Expected a URI without user information")
    assert(uri.authority.port == 0, "Expected a URI without a port")
    assert(uri.query.isEmpty, "Expected a URI without a query")
    assert(uri.fragment.isEmpty, "Expected a URI without a fragment")
    assert(uri.authority.host.address.nonEmpty, "Expected a URI with a host")

    ProxyLocation(uri.authority.host, uri.path)
  }

  def apply(host: String, path: String): ProxyLocation = {
    ProxyLocation(Uri.Host(host), Uri.Path(path))
  }
}

case class ProxyLocation(host: Uri.Host,
                         path: Uri.Path = Uri.Path.Empty) {
  override def toString = host.address + path.toString()
}

case class ProxyConf(location: ProxyLocation,
                     upstream: Uri,
                     headers: List[Header] = List(),
                     metadata: Option[JObject] = None)

case class Header(name: String, value: String)
