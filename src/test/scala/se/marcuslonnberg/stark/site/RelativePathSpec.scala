package se.marcuslonnberg.stark.site

import org.scalatest.{FreeSpec, Matchers}
import se.marcuslonnberg.stark.site.Implicits._
import spray.http.Uri
import spray.http.Uri.Path

class RelativePathSpec extends FreeSpec with Matchers {

  implicit class PathString(request: String) {
    def shouldBeRelativeTo(site: String): Unit = {
      registerTest(s"'$site' should match request '$request'") {
        assert(Uri.Path(request).isRelativeTo(Uri.Path(site)))
      }
    }

    def shouldNotBeRelativeTo(site: String): Unit = {
      registerTest(s"'$site' should not match request '$request'") {
        assert(!Uri.Path(request).isRelativeTo(Uri.Path(site)))
      }
    }
  }

  "isRelativeTo" - {
    "" shouldBeRelativeTo ""
    "/" shouldBeRelativeTo ""
    "/" shouldBeRelativeTo "/"
    "//" shouldBeRelativeTo "/"
    "/abc" shouldBeRelativeTo "/"
    "/abc" shouldBeRelativeTo "/abc"
    "/abc/" shouldBeRelativeTo "/abc"
    "/abc/" shouldBeRelativeTo "/abc/"
    "/abc/xyz" shouldBeRelativeTo "/abc"
    "/abc/xyz" shouldBeRelativeTo "/abc/"

    "" shouldNotBeRelativeTo "/"
    "/" shouldNotBeRelativeTo "//"
    "" shouldNotBeRelativeTo "/abc"
    "/xyz" shouldNotBeRelativeTo "/abc"
    "/abcxyz" shouldNotBeRelativeTo "/abc"
    "/abc" shouldNotBeRelativeTo "/abc/"
    "/abc" shouldNotBeRelativeTo "/abc/xyz"
  }

  "relativizeTo" - {
    Path("").relativizeTo(Path("")) shouldEqual Some(Path(""))
    Path("/").relativizeTo(Path("")) shouldEqual Some(Path("/"))
    Path("/abc").relativizeTo(Path("")) shouldEqual Some(Path("/abc"))
    Path("/abc").relativizeTo(Path("/")) shouldEqual Some(Path("/abc"))
  }
}
