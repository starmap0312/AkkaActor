package akka_stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Framing, Tcp}
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.StdIn

// write a simple socket client with Akka Streams
object SimpleWebClient extends App {
  implicit val system = ActorSystem("SimpleWebClient")
  implicit val materializer = ActorMaterializer()

  val outgoingConnectionFlow: Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]] =
    Tcp().outgoingConnection("127.0.0.1", 9000)
  val flow: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]
    .via( // consuming the infinite entity stream and applying a framing to it
      Framing.delimiter(
        ByteString("\n"),
        maximumFrameLength = 256,
        allowTruncation = true
      )
    )
    .map(_.utf8String)
    .map(println)
    .map(_ => StdIn.readLine("> "))
    .map(_ + "\n")
    .map(ByteString(_))
  val matValue: Future[Tcp.OutgoingConnection] = outgoingConnectionFlow.join(flow).run()
  // Join this Flow to another Flow, by cross connecting the inputs and outputs

  //  in contrast to the simple web server, we don't have to manage the incoming connections anymore
}
