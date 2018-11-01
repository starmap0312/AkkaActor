package akka_http.client

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods // the entry point for akka-http (if writing java, use java dsl instead)
//import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Source

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success}

// Web application vs. Web service:
// 1) Web application: ex. play framework, which gives lots of structure and features for web application development
// 2) Web service: ex. akka-http, which gives lots of infrastructure to expose an existing service via HTTP
// akka-http vs. spray
// 1) akka-http is based on akka-stream (which deals with streaming in a nice way)
// 2) spray is based on akka-actor
// akka-http related modules
// 1) akka-http: the important one, on top of akka-http-core
// 2) akka-http-core: low-level implementation
// 3) akka-http-testkit: for testing
// 4) akka-http-spray-json and akka-http-xml: integration with serialization libraries
object Step0SimpleClient extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val request: HttpRequest = HttpRequest(method = HttpMethods.GET, uri = "http://localhost:9000/abc")
  val responseFuture: Future[HttpResponse] = Http().singleRequest(request)
  val sourceFuture: Future[Source[String, Any]] = responseFuture.map { response =>
    response.entity.dataBytes. // Source[ByteString, Any]
      //via(Gzip.decoderFlow). // conversion flow for decoding Gzipped content bytestring
      map(_.utf8String)        // Source[String, Any]: converting ByteString to (utf8) String
  }
  sourceFuture onComplete {
    case Success(source) =>
      source.runForeach(println)
    case Failure(ex) =>
      println("we have failure")
      ex.printStackTrace()
      system.terminate()
  }
  StdIn.readLine()
  //system.terminate()
}