package akka_stream

import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, RunnableGraph, Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.StdIn

object StreamingIO extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // simple Sources and Sinks that work with ByteString instances to perform IO operations on files
  val path: Path = Paths.get("src/main/resources/example.csv") // file path relative to project folder
  val fileSource: Source[ByteString, Future[IOResult]] = FileIO.fromPath(path)
  val stream: RunnableGraph[Future[IOResult]] = fileSource.
    to(Sink.ignore)
  val matValue: Future[IOResult] = stream.run() // the stream is materialized as IOResult
  StdIn.readLine()
  system.terminate()

}
