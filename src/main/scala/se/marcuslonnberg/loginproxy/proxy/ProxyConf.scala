package se.marcuslonnberg.loginproxy.proxy

import spray.http.Uri

case class ProxyConf(host: Host, upstream: Uri, headers: List[Header] = List())

case class Host(address: String, path: Option[Uri.Path] = None)

case class Header(name: String, value: String)
