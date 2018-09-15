package akka_http.server

package akka_http.server

package akka_http.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.io.StdIn
import scala.util.{Failure, Success}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString

object HttpEntityExample extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  val route: Route =
    get {
      // curl http://localhost:9000/fixed -v
      path("fixed") {
        complete {
          // the following are equivalent:
          "Hello World"
          HttpEntity(ContentTypes.`text/plain(UTF-8)`, ByteString("Hello World"))
          HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString("Hello World"))
          HttpResponse(entity =
            // case class Strict: the model for the entity of a "regular" unchunked HTTP message with known, fixed data
            HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString("Hello World"))
          )
        }
      } ~
      // curl http://localhost:9000/stream -v
      path("stream") {
        complete {
          HttpResponse(entity =
            // case class Default: the model for the entity of a "regular" unchunked HTTP message with a known non-zero length
            HttpEntity.Default(
              ContentTypes.`text/plain(UTF-8)`,
              // ContentLength: this specifies the content stream length
              // we will get error: "curl: (56) Recv failure: Connection reset by peer" if the actual length exceeds
              1000,
              // Content: this is supplied as a Stream
              //Source.single(ByteString("Hello")) ++ Source.single(ByteString(" World"))
              Source(1 to 1000).map(num => ByteString((num % 10).toString))
            )
          )
        }
      } ~
      // curl http://localhost:9000/chunked -v
      path("chunked") {
        complete {
          HttpResponse(entity =
            // case class Chunked: the model for the entity of a chunked HTTP message (with `Transfer-Encoding: chunked`).
            HttpEntity.Chunked.fromData(
              ContentTypes.`text/plain(UTF-8)`,
              // Content: this is supplied as a Stream
              //Source.single(ByteString("Hello")) ++ Source.single(ByteString(" World"))
              Source(1 to 1000).map(num => ByteString(num.toString))
            )
          )
        }
      } ~
      // note: Connection #0 to host localhost left intact
      //   when the above transfer is over, the TCP session itself is still open (i.e no FIN/ACK exchanges have been made)
      //   this allows you to keep reusing the same TCP connection for multiple transfers
      // to explicitly close the connection when finished sending, use the following ClosedDelimited case class
      // curl http://localhost:9000/closed -v
      path("closed") {
        complete {
          HttpResponse(entity =
            // case class ClosedDelimited: the model for the entity of an HTTP response that is terminated by the server closing the connection.
            //   The content-length of such responses is unknown at the time the response headers have been received.
            HttpEntity.CloseDelimited(
              ContentTypes.`text/plain(UTF-8)`,
              // Content: this is supplied as a Stream
              //Source.single(ByteString("Hello")) ++ Source.single(ByteString(" World"))
              Source(1 to 1000).map(num => ByteString(num.toString))
            )
          )
        }
      }
    }

  Http().bindAndHandleAsync(Route.asyncHandler(route), "localhost", 9000).
    onComplete {
      case Success(_) =>
        println("Server started on port 9000. Type Enter to terminate")
        StdIn.readLine()
        system.terminate()
      case Failure(ex) =>
        println("Binding failed")
        ex.printStackTrace()
        system.terminate()
    }
}
