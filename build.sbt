organization := "feh.tec"

name := "schedule-neg"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.7"

resolvers += "Fehu's github repo" at "http://fehu.github.io/repo"

lazy val root = (project in file(".")) dependsOn comm

lazy val comm = RootProject(file( "../../negotiation" ))

libraryDependencies += "org.apache.poi" % "poi" % "3.12"

libraryDependencies += "org.apache.poi" % "poi-ooxml" % "3.12"

libraryDependencies += "org.reactivemongo" %% "reactivemongo" % "0.11.7"

libraryDependencies += "com.typesafe.play" %% "play-iteratees" % "2.4.0"


scalacOptions in (Compile, doc) ++= Seq("-diagrams", "-diagrams-max-classes", "50", "-diagrams-max-implicits", "20")