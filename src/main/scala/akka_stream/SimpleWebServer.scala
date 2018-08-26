package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Framing, Sink, Source, Tcp}
import akka.util.ByteString

import scala.concurrent.Future
import scala.util.{Failure, Success}

// write a simple socket server with Akka Streams
object SimpleWebServer extends App {

  implicit val system = ActorSystem("SimpleWebServer")
  implicit val materializer = ActorMaterializer()

  val serverLogicFlow: Flow[ByteString, ByteString, NotUsed] = {
    val delimiter = Framing.delimiter(
      ByteString("\n"),
      maximumFrameLength = 256,
      allowTruncation = true
    ) // split sequence of bytes in lines, whenever found a newline character

    val receiver = Flow[ByteString].map { bytes =>
      val message = bytes.utf8String
      println(s"Server received: $message")
      message
    } // converted ByteString (sequence of bytes) into a string (note: only printable ASCII characters should be converted)

    val responder = Flow[String].map { message =>
      val answer = s"Server hereby responds to message: $message\n"
      ByteString(answer)
    } // convert the answer string back to a ByteString (sequence of bytes)

    Flow[ByteString] // connect all of the flows through the via() method
      .via(delimiter)
      .via(receiver)
      .via(responder)
  }

  def mkServer(address: String, port: Int)(implicit system: ActorSystem, materializer: Materializer): Unit = {
    import system.dispatcher

    val incomingConnectionSource: Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]] =
      Tcp().bind(address, port)          // this takes input requests

    val connectionHandlerSink: Sink[Tcp.IncomingConnection, Future[Done]] =
      Sink.foreach[Tcp.IncomingConnection] { conn =>
        println(s"Incoming connection from: ${conn.remoteAddress}")
        conn.handleWith(serverLogicFlow) // this produces output responses
      }

    val streamRan: Future[Tcp.ServerBinding] =
      incomingConnectionSource.to(connectionHandlerSink).run()

    streamRan onComplete {
      case Success(b) =>
        println(s"Server started, listening on: ${b.localAddress}")
      case Failure(e) =>
        println(s"Server could not be bound to $address:$port: ${e.getMessage}")
    }
  }

  mkServer("localhost", 9000) // Server started, listening on: /127.0.0.1:9000
  // test the socket server
  // ex. echo "Hello World" | nc localhost 9000
  //   Incoming connection from: /127.0.0.1:53771
  //   Server received: Hello World <-> Server hereby responds to message: Hello World

  // use flow.map() to build a Flow from smaller flows
  val serverLogicFlow2 = Flow[ByteString]
    .via(Framing.delimiter(
      ByteString("\n"),
      maximumFrameLength = 256,
      allowTruncation = true))
    .map(_.utf8String)
    .map(msg => s"Server hereby responds to message: $msg\n")
    .map(ByteString(_))

}
