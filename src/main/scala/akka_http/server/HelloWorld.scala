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

  def handler(request: HttpRequest): Future[HttpResponse] = {
    // we can handle the request asynchronously here, i.e. we don't need to return HttpResponse immediately
    request match {
      // handle requests that match the following path, i.e. curl http://localhost:9000/abc
      case HttpRequest(HttpMethods.GET, Uri.Path("/abc"), _, _, _) =>
        // 1) returns Success:
        //    always return "Hello World" regardless of the HttpRequest
        Future.successful(HttpResponse(entity = "Hello World"))
        // 2) returns Failure:
        //    internal server error, sending 500 response: i.e. HTTP/1.1 500 Internal Server Error
        //Future.failed(new Exception("an exception occurs"))
    }
    // in case of requesting other uri path, Failure(scala.MatchError) is returned
    //   so the client will receive HTTP/1.1 500 Internal Server Error
  }

  // 1) Http().bindAndHandleAsync([handler], [interface], [port]):
  //    bind to the network address and specify a handler for it
  //    a handler is a function from HttpRequest to Future[HttpResponse]
  Http().bindAndHandleAsync(handler, "localhost", 9000).
    onComplete {
      case Success(_) =>
        println("Server started on port 9000. Type Enter to terminate")
        StdIn.readLine() // if the binding is successful, block the execution, i.e. not terminating the program
        system.terminate()
      case Failure(ex) =>
        println("Binding failed")
        ex.printStackTrace() // if the binding is failed, print out the exception & terminate the program
        system.terminate()
    }
}
