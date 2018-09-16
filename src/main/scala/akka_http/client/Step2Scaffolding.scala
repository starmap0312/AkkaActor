package akka_http.client

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future

object Step2Scaffolding extends Scaffolding with App {

  def sourceFuture: Future[Source[String, Any]] = {
    Http().singleRequest(request).map { response =>
      response.entity.dataBytes. // Source[ByteString, Any]
        map(_.utf8String) // Source[String, Any]
    }
  }

  runWebService {
    get {
      onSuccess(sourceFuture) { source =>
        complete {
          HttpResponse(
            entity = HttpEntity.Chunked.fromData(
              ContentTypes.`text/plain(UTF-8)`,
              source.map(str => ByteString(str, "UTF8"))
            )
          )
        }
      }
    }
  }
}