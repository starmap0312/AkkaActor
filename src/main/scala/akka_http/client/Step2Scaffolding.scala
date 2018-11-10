package akka_http.client

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Framing, Source}
import akka.util.ByteString

import scala.concurrent.Future

object Step2Scaffolding extends Scaffolding with App {

  val request: HttpRequest = HttpRequest(uri = "http://localhost:9000/")

  def sourceFuture: Future[Source[String, Any]] = {
    Http().singleRequest(request).map { response =>
      response.entity.dataBytes. // Source[ByteString, Any]
        via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true)).
        map(_.utf8String).       // Source[String, Any]
        map(_ + "!!!").
        via(printFlow)           // Source[String, Any], via a Flow that prints & returns the string itself
    }
  }

  implicit val port: Int = 9001
  runWebService {
    get {
      onSuccess(sourceFuture) { source =>
        complete {
          HttpResponse(
            entity = HttpEntity.Chunked.fromData(
              ContentTypes.`text/plain(UTF-8)`,
              source.map(line => ByteString(line + "\n", "UTF8"))
            )
          )
        }
      }
    }
  }

  def printFlow: Flow[String, String, NotUsed] = Flow[String].map {
    line =>
      println(line.slice(0, 10) + "...") // a Flow that just prints part of the string
      line
  }
}