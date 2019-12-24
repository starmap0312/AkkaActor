
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
  "org.jsoup" % "jsoup" % "1.8.3",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.9.7",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.7",
  "org.slf4j" % "slf4j-api" % "1.7.+",
  // packages for testing
  //"junit" % "junit" % "4.11" % "test",
  "org.scalatest" %% "scalatest" % "3.+" % Test,
  "org.scalacheck" %% "scalacheck" % "1.+" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
)
// sbt packageBin:
//   this creates the package ./target/scala-2.12/akkaactor_2.12-0.1.jar
// sbt publishLocal:
//   this creates the package ~/.ivy2/local/default/akkaactor_2.12/0.1/jars/akkaactor_2.12.jar

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

enablePlugins(AkkaGrpcPlugin) // for akka grpc
// sbt compile:
//   this generates code from the definition of .proto files
// ALPN agent
enablePlugins(JavaAgent) // for akka grpc
javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"