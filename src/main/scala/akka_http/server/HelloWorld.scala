package akka_http.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success}

object HelloWorld extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  def handler(request: HttpRequest) = {
    // ex1. always return "Hello World" regardless of the HttpRequest, i.e. curl http://localhost:9000
    //Future.successful(HttpResponse(entity = "Hello World"))
    // ex2. if request matches the following path, i.e. curl http://localhost:9000/abc
    request match {
      case HttpRequest(HttpMethods.GET, Uri.Path("/abc"), _, _, _) =>
        Future.successful(HttpResponse(entity = "Hello World"))
    }
  }
  Http().bindAndHandleAsync(handler, "localhost", 9000).
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
