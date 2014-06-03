package se.marcuslonnberg.stark.proxy

import spray.http.Uri
import org.json4s.JsonAST.JObject

case class ProxyConf(host: Host, upstream: Uri, headers: List[Header] = List(), metadata: Option[JObject] = None)

case class Host(address: String, path: Option[Uri.Path] = None)

case class Header(name: String, value: String)
