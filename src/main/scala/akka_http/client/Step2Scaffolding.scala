package akka_http.client

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString

import scala.concurrent.Future

object Step2Scaffolding extends Scaffolding with App {

  def sourceFuture: Future[Source[String, Any]] = {
    Http().singleRequest(request).map { response =>
      response.entity.dataBytes. // Source[ByteString, Any]
        map(_.utf8String).       // Source[String, Any]
        via(printFlow)           // Source[String, Any], via a Flow that prints & returns the string itself
    }
  }

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
      println(line.slice(1, 80) + "...") // a Flow that just prints part of the string
      line
  }
}