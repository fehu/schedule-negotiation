organization := "feh.tec"

name := "schedule-neg"

version := "0.0"

scalaVersion := "2.11.6"

resolvers += "Fehu's github repo" at "http://fehu.github.io/repo"

lazy val root = (project in file(".")) dependsOn comm

lazy val comm = RootProject(file( "../../negotiation" ))

libraryDependencies += "org.apache.poi" % "poi" % "3.12"

libraryDependencies += "org.apache.poi" % "poi-ooxml" % "3.12"

libraryDependencies += "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23"

libraryDependencies += "com.typesafe.play" %% "play-iteratees" % "2.4.0"
