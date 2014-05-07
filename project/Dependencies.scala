import sbt._

object Dependencies {
  val Akka = {
    val version = "2.3.2"
    Seq("com.typesafe.akka" %% "akka-actor" % version,
      "com.typesafe.akka" %% "akka-slf4j" % version)
  }

  val Spray = {
    val version = "1.3.1"
    Seq("io.spray" % "spray-can" % version,
      "io.spray" % "spray-client" % version,
      "io.spray" % "spray-routing" % version,
      "io.spray" % "spray-testkit" % version)
  }

  val Json4s = {
    Seq("org.json4s" %% "json4s-native" % "3.2.9")
  }

  val ScalaTest = {
    Seq("org.scalatest" %% "scalatest" % "2.1.5" % "test")
  }

  object Resolvers {
    val Spray = Seq(
      "spray repo" at "http://repo.spray.io/"
    )

    val all = Spray
  }

}
