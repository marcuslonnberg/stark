package se.marcuslonnberg.stark.site

import spray.http.Uri

object Implicits {

  implicit class RelativePath(val path: Uri.Path) {
    def relativizeTo(base: Uri.Path): Option[Uri.Path] = {
      if (path.startsWith(base)) {
        val chopped = path.dropChars(base.charCount)
        if (base.reverse.startsWithSlash || !chopped.startsWithSegment) {
          if (chopped.startsWithSegment) Some(Uri.Path./ ++ chopped)
          else Some(chopped)
        } else None
      } else None
    }

    def isRelativeTo(base:Uri.Path) = relativizeTo(base).isDefined
  }

}
