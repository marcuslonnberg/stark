import Dependencies._

name := "login-proxy"

organization := "se.marcuslonnberg"

version := "1.0"

libraryDependencies ++= Akka ++ Spray ++ Json4s ++ ScalaTest

resolvers ++= Dependencies.Resolvers.all
