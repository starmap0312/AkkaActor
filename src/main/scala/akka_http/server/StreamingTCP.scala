package akka_http.server

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Tcp.ServerBinding
import akka.stream.scaladsl.{Flow, Framing, Source, Tcp}
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.StdIn

object StreamingTCP extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  //import system.dispatcher

  val connections: Source[Tcp.IncomingConnection, Future[ServerBinding]] =
    Tcp().bind("127.0.0.1", 9000)
    // a Source of Tcp.IncomingConnection and can be materialized to Future[Tcp.ServerBinding]]

  val matValue: Future[Done] = connections runForeach { connection =>
    println(s"New connection from: ${connection.remoteAddress}")
    val echoFlow: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]. // server logic
      via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 254, allowTruncation = true)).
      map(_.utf8String).
      map(_ + "!!!\n").
      map(ByteString(_))
    connection.handleWith(echoFlow) // we can handle each Tcp.IncomingConnection using a Flow
    // note: since the incoming connection Flow directly corresponds to an existing, already accepted connection
    // its handling can only ever be materialized once (i.e. not reusable)
  }
  // ex. testing
  // echo -n "Hello World" | nc 127.0.0.1 9000
  // Hello World!!!
  StdIn.readLine()
  system.terminate()
}
