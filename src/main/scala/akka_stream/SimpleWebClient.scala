package akka_stream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Framing, Tcp}
import akka.util.ByteString

import scala.io.StdIn

// write a simple socket client with Akka Streams
object SimpleWebClient extends App {
  implicit val system = ActorSystem("SimpleWebServer")
  implicit val materializer = ActorMaterializer()

  val outgoingConnectionFlow = Tcp().outgoingConnection("localhost", 9000)
  val flow = Flow[ByteString]
    .via(
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
  outgoingConnectionFlow.join(flow).run()
  // Join this Flow to another Flow, by cross connecting the inputs and outputs

  //  in contrast to the simple web server, we don't have to manage the incoming connections anymore
}
