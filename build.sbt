
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
  "org.slf4j" % "slf4j-api" % "1.7.+",
  "junit" % "junit" % "4.11" % "test"
)