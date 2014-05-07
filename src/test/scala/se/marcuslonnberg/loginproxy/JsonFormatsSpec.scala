package se.marcuslonnberg.loginproxy

import org.scalatest.{Inside, Matchers, FreeSpec}
import org.json4s.JsonDSL._
import spray.http.Uri
import org.json4s.{Extraction, DefaultFormats}
import org.json4s.native.Serialization._
import se.marcuslonnberg.loginproxy.proxy.{Header, Host, ProxyConf}

class JsonFormatsSpec extends FreeSpec with Matchers with Inside {
  implicit val formats = DefaultFormats + JsonFormats.uriFormat + JsonFormats.listHeaderFormat + JsonFormats.proxyFormat

  "Proxy" - {
    "Basic" - {
      val json =
        """{
          |  "host":{
          |    "address":"test.local"
          |  },
          |  "upstream":"http://test.example.com/xyz",
          |  "headers":{
          |    "Authorization":"Basic 123"
          |  }
          |}""".stripMargin

      val proxy = ProxyConf(Host("test.local"), Uri("http://test.example.com/xyz"), List(Header("Authorization", "Basic 123")))

      "Deserialize" - {
        read[ProxyConf](json) shouldEqual proxy
      }

      "Serialize" - {
        writePretty(proxy) shouldEqual json
      }
    }

    "Extensive" - {
      val json = ("host" -> ("address" -> "test.local")) ~
        ("upstream" -> "http://test.example.com/xyz") ~
        ("headers" ->
          ("Authorization" -> "Basic 123"))

      val proxy = ProxyConf(Host("test.local"), Uri("http://test.example.com/xyz"), List(Header("Authorization", "Basic 123")))

      "Deserialize" - {
        json.extract[ProxyConf] shouldEqual proxy
      }

      "Serialize" - {
        Extraction.decompose(proxy) shouldEqual json
      }
    }
  }
}
