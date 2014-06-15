package se.marcuslonnberg.stark.proxy

import spray.http.Uri
import org.json4s.JsonAST.JObject

case class ProxyConf(host: Uri.Host,
                     path: Uri.Path = Uri.Path.Empty,
                     upstream: Uri,
                     headers: List[Header] = List(),
                     metadata: Option[JObject] = None)

case class Header(name: String, value: String)
