organization := "feh.tec"

name := "schedule-neg"

version := "0.0"

scalaVersion := "2.11.6"

lazy val root = (project in file(".")) dependsOn comm

lazy val comm = RootProject(file( "../../negotiation" ))

