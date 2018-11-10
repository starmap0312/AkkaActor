
name := "AkkaActor"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.1",
  "com.typesafe.akka" %% "akka-actor" % "2.+",
  "com.typesafe.akka" %% "akka-slf4j" % "2.+",
  "com.typesafe.akka" %% "akka-http"   % "10.1.1",
  "com.typesafe.akka" %% "akka-stream" % "2.5.11",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.1", // akka JSON marshaller Support
  "org.jsoup" % "jsoup" % "1.8.3",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.9.7",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.7",
  "org.slf4j" % "slf4j-api" % "1.7.+",
  // packages for testing
  //"junit" % "junit" % "4.11" % "test",
  "org.scalatest" %% "scalatest" % "3.+" % Test,
  "org.scalacheck" %% "scalacheck" % "1.+" % Test,
  "com.typesafe.akka" %% "akka-testkit" % "2.+" % Test,
)