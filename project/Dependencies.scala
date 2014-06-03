import sbt._

object Dependencies {
  val Akka = {
    val version = "2.3.3"
    Seq("com.typesafe.akka" %% "akka-actor" % version,
      "com.typesafe.akka" %% "akka-slf4j" % version)
  }

  val Spray = {
    val version = "1.3.1-20140423"
    Seq("io.spray" %% "spray-can" % version,
      "io.spray" %% "spray-client" % version,
      "io.spray" %% "spray-routing" % version,
      "io.spray" %% "spray-testkit" % version)
  }

  val Json4s = Seq("org.json4s" %% "json4s-native" % "3.2.9")

  val ScalaTest = Seq("org.scalatest" %% "scalatest" % "2.1.7" % "test")

  val TypeSafeConfig = {
    Seq("com.typesafe" % "config" % "1.2.0",
      "net.ceedubs" %% "ficus" % "1.1.1")
  }

  val Logback = Seq("ch.qos.logback" % "logback-classic" % "1.1.1")

  val NScala = Seq("com.github.nscala-time" %% "nscala-time" % "1.0.0")

  val CommonsCodec = Seq("commons-codec" % "commons-codec" % "1.9")

  val all = Akka ++ Spray ++ Json4s ++ ScalaTest ++ TypeSafeConfig ++ Logback ++ NScala ++ CommonsCodec

  object Resolvers {
    val Spray = Seq(
      "spray repo" at "http://repo.spray.io/"
    )

    val all = Spray
  }

}
