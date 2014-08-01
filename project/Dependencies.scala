import sbt._

object Dependencies {
  val Akka = {
    val version = "2.3.4"
    Seq("com.typesafe.akka" %% "akka-actor" % version,
      "com.typesafe.akka" %% "akka-slf4j" % version)
  }

  val Spray = {
    val version = "1.3.1"
    Seq("io.spray" %% "spray-can" % version,
      "io.spray" %% "spray-client" % version,
      "io.spray" %% "spray-routing" % version,
      "io.spray" %% "spray-testkit" % version)
  }

  val Json4s = {
    val version = "3.2.10"
    Seq("org.json4s" %% "json4s-native" % version,
      "org.json4s" %% "json4s-ext" % version)
  }

  val ScalaTest = Seq("org.scalatest" %% "scalatest" % "2.2.1" % "test")

  val TypeSafeConfig = {
    Seq("com.typesafe" % "config" % "1.2.1",
      "net.ceedubs" %% "ficus" % "1.1.1")
  }

  val Logback = Seq("ch.qos.logback" % "logback-classic" % "1.1.2")

  val NScala = Seq("com.github.nscala-time" %% "nscala-time" % "1.2.0")

  val CommonsCodec = Seq("commons-codec" % "commons-codec" % "1.9")

  val RedisScala = Seq("com.etaty.rediscala" %% "rediscala" % "1.3.1")

  val all = Akka ++ Spray ++ Json4s ++ ScalaTest ++ TypeSafeConfig ++ Logback ++ NScala ++ CommonsCodec ++ RedisScala

  object Resolvers {
    val Spray = "spray repo" at "http://repo.spray.io/"

    val RedisScala = "rediscala" at "http://dl.bintray.com/etaty/maven"

    val all = Spray :: RedisScala :: Nil
  }

}
