name := "stark"

organization := "se.marcuslonnberg"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.1"

libraryDependencies ++= Dependencies.all

resolvers ++= Dependencies.Resolvers.all

Revolver.settings

Revolver.enableDebugging(port = 5005, suspend = false)
