package se.marcuslonnberg.stark

import org.json4s.Extraction
import org.json4s.JsonDSL._
import org.scalatest.{FreeSpec, Inside, Matchers}
import se.marcuslonnberg.stark.site.{Header, Location, ProxyConf}
import spray.http.Uri

class JsonFormatsSpec extends FreeSpec with Matchers with Inside with JsonSupport {
  "Proxy" - {
    "Basic" - {
      val json = ("location" -> "test.local") ~
        ("upstream" -> "http://test.example.com/xyz") ~
        ("headers" ->
          List(("name" -> "Authorization") ~ ("value" -> "Basic 123")))

      val proxy = ProxyConf(Location(Uri.Host("test.local")), upstream = Uri("http://test.example.com/xyz"), headers = List(Header("Authorization", "Basic 123")))

      "Deserialize" - {
        json.extract[ProxyConf] shouldEqual proxy
      }

      "Serialize" - {
        Extraction.decompose(proxy) shouldEqual json
      }
    }
  }
}
