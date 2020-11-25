
name := "AkkaActor"

version := "0.1"

scalaVersion := "2.12.6"
val akkaVersion = "2.6.+"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.1",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.+", // akka JSON marshaller Support
  "com.typesafe.akka" %% "akka-http"   % "10.+",
  "com.typesafe.akka" %% "akka-http-caching" % "10.+", // to use CachingDirectives in route
  "org.jsoup" % "jsoup" % "1.8.3",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.9.7",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.7",
  "org.slf4j" % "slf4j-api" % "1.7.+", // required if to use log.info(...) in ActorLogging
  "org.slf4j" % "slf4j-simple" % "1.7.+", // required if to use log.info(...) in ActorLogging
  "ch.qos.logback" % "logback-classic" % "1.2.3", // for logback.xml
  // packages for testing
  //"junit" % "junit" % "4.11" % "test",
  "org.scalatest" %% "scalatest" % "3.+" % Test,
  "org.scalacheck" %% "scalacheck" % "1.+" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test, // to test akka stream, ex. SourceProbe
)
// sbt packageBin:
//   this creates the package ./target/scala-2.12/akkaactor_2.12-0.1.jar
// sbt publishLocal:
//   this creates the package ~/.ivy2/local/default/akkaactor_2.12/0.1/jars/akkaactor_2.12.jar

// Ultrabrew Metrics: a lightweight, high-performance Java library to measure the behavior of components
// https://github.com/ultrabrew/metrics
ThisBuild / resolvers += "ultrabrew" at "https://dl.bintray.com/ultrabrew/m2"
libraryDependencies ++= Seq(
  "io.ultrabrew.metrics" % "metrics-core" % "0.6.0",
)

enablePlugins(JavaAppPackaging) // for building distribution files with executables
// Use of the sbt-native-packager plugin allows you to run the following commands:
//   add "addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.5.2")" to file: project/plugins.sbt
// sbt universal:packageBin:
//   this creates a universal zip file for distribution: target/universal/akkaactor-0.1.zip
//   unzipping the file, you get:
//     in folder akkaactor-0.1/lib/, all the *.jar files used and your app jar file: default.akkaactor-0.1.jar
//     in folder akkaactor-0.1/bin/, all the executable files for Scala object with main method defined
//     ex. akkaactor-0.1/bin/simple-server, and you can run the executable simply by: ./akkaactor-0.1/bin/simple-server
// sbt docker:publishLocal:
//   this builds a local docker image named: akkaactor and also creates a folder target/docker/stage/ with files:
//     target/docker/stage/opt/docker/Dockerfile
//     target/docker/stage/opt/docker/bin/*
//     target/docker/stage/opt/docker/lib/*
//   you can then run simply as: docker run -it ffe9041f5f18
//     However, by default the built docker image has ENTRYPOINT ["/opt/docker/bin/akkaactor"]
//     so it assumes that there is a Scala class named as the project with main method defined to be run as an executable
//     To run the docker bash, you can override the entrypoint by: docker run -it --entrypoint "/bin/bash" ffe9041f5f18
// sbt debian:packageBin
//   this creates a debian package
// sbt rpm:packageBin: this generates an rpm
//   this creates a rpm package

enablePlugins(AkkaGrpcPlugin) // for akka grpc client & server
// sbt compile:
//   this generates code from the definition of .proto files
// ALPN agent
enablePlugins(JavaAgent) // for akka grpc server (Plugin for adding Java agents to projects, a JVM agent that enables TLS ALPN/NPN extension)
javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"

// sbt Multi-project builds 
lazy val subProject = (project in file("subproject"))
// by running sbt universal:packageBin, it will create: subproject/target/scala-2.12/subproject_2.12-0.2.jar
lazy val mainProject = (project in file(".")).dependsOn(subProject)
// in akkaactor.akkaactor-0.1.jar, it will include subproject.subproject-0.2.jar
// therefore, you can use any classes/objects/packages defined in the subproject in your main project


// 1) sbt run:
//    if there are multiple main classes detected, you need to select one to run, ex.
//      [1] Akkaactor
//      [2] ComposableFuturesWithAkka
//      [3] akka_actors.ActorTimeout
//    unless you specify the main class to run here:
Compile / mainClass := Some("Akkaactor")
// 2) sbt "project subProject" run
//    run the sub-project
//