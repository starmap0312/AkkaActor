package akka_stream

import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import akka.stream.scaladsl.Framing
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, RunnableGraph, Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.StdIn

// simple Sources and Sinks that work with ByteString instances to perform IO operations on files
object StreamingIO extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  //import system.dispatcher

  // 1) FileIO.fromPath([Path]): returns Source[ByteString, Future[IOResult]]
  //    FileIO.toPath([Path]):   returns Sink[ByteString, Future[IOResult]]
  println(System.getProperty("user.dir"))
  val filepath: Path = Paths.get("src/main/resources/example.csv") // file path relative to project folder
  val fileSource: Source[ByteString, Future[IOResult]] = FileIO.fromPath(filepath)
  val stream: RunnableGraph[Future[IOResult]] = fileSource.
    via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true)).
    map(_.utf8String). // map the ByteString of each-line to utf-8 String
    map(_ + "!!!\n").  // append line break to the utf-8 String of each-line
    map(x => {print(x); x}). // 140.112.1.1,A1!!! ...
    map(ByteString(_)).
    to(FileIO.toPath(Paths.get("example.out.csv"))) // 140.112.1.1,A1!!!\n ....
  val matValue: Future[IOResult] = stream.run() // the stream is materialized as IOResult
  StdIn.readLine()
  system.terminate()
}
