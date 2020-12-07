ThisBuild / version := "0.2"

val akkaVersion = "2.6.+"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.1",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "org.slf4j" % "slf4j-api" % "1.7.+", // required if to use log.info(...) in ActorLogging
  "org.slf4j" % "slf4j-simple" % "1.7.+", // required if to use log.info(...) in ActorLogging
)
