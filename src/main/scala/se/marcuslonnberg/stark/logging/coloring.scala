package se.marcuslonnberg.stark.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.color.{ANSIConstants, ForegroundCompositeConverterBase}

object Colors {
  val GRAY_FG = "90"
}

abstract class Coloring(infoColor: String = ANSIConstants.BLUE_FG) extends ForegroundCompositeConverterBase[ILoggingEvent] {
  override def getForegroundColorCode(event: ILoggingEvent) = {
    event.getLevel match {
      case Level.ERROR => ANSIConstants.RED_FG
      case Level.WARN => ANSIConstants.YELLOW_FG
      case Level.INFO => infoColor
      case Level.DEBUG => Colors.GRAY_FG
      case _ => ANSIConstants.DEFAULT_FG
    }
  }
}

class LevelColoring extends Coloring(ANSIConstants.BLUE_FG)

class MessageColoring extends Coloring(ANSIConstants.DEFAULT_FG)
