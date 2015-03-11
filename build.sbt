name := "stark"

organization := "se.marcuslonnberg"

version := "0.1.1-SNAPSHOT"

scalaVersion := "2.11.6"

libraryDependencies ++= Dependencies.all

resolvers ++= Dependencies.Resolvers.all

assemblyMergeStrategy in assembly := {
  case PathList("org", "mockito", x @ _*) if x.lastOption.exists(_.endsWith(".class")) => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

Revolver.settings

Revolver.enableDebugging(port = 5005, suspend = false)

enablePlugins(DockerPlugin)

docker <<= (docker dependsOn assembly)
dockerfile in docker := {
  val appFile = (assemblyOutputPath in assembly).value
  val targetAppDir = "/srv/stark"
  val targetAppPath = s"$targetAppDir/${name.value}.${appFile.ext}"
  new Dockerfile {
    from("dockerfile/java")
    workDir(targetAppDir)
    entryPointShell("java", "$JAVA_OPTIONS", "-jar", targetAppPath)
    add(appFile, targetAppPath)
  }
}

imageNames in docker := Seq(
  ImageName("marcuslonnberg/" + name.value +":" + version.value)
)
