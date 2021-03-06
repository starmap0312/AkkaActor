addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.5.2")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "0.7.3") // for akka grpc client & server
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4") // for akka grpc (ALPN agent)
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2") // for showing dependency tree: ex. sbt dependencyGraph
