import Dependencies._

name := "login-proxy"

organization := "se.marcuslonnberg"

version := "1.0"

libraryDependencies ++= Akka ++ Spray

resolvers ++= Dependencies.Resolvers.all
