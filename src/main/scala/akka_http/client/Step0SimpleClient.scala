package akka_http.client

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
//import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Source

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success}

object Step0SimpleClient extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val request: HttpRequest = HttpRequest(uri = "http://localhost:9000/abc")
  val responseFuture: Future[HttpResponse] = Http().singleRequest(request)
  val sourceFuture: Future[Source[String, Any]] = responseFuture.map { response =>
    response.entity.dataBytes. // Source[ByteString, Any]
      //via(Gzip.decoderFlow). // conversion flow for decoding Gzipped content bytestring
      map(_.utf8String)        // Source[String, Any]: converting ByteString to (utf8) String
  }
  sourceFuture onComplete {
    case Success(source) => source.runForeach(println)
    case Failure(ex) =>
      ex.printStackTrace()
      system.terminate()
  }
  StdIn.readLine()
  system.terminate()
}