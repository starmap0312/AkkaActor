package akka_http.server

package akka_http.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.io.StdIn
import scala.util.{Failure, Success}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object RouteExample extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  // Routing
  val route: Route =
    // Directives: ex. get, path for matching (filtering) requests
    get {                       // i.e. HttpMethods.GET
      // 1) curl http://localhost:9000/abc
      path("abc") {        // i.e. Uri.Path("/abc")
        // leaf Directive: ex. complete, redirect, for specifying responses
        complete("Hello World") // i.e. Future.successful(HttpResponse(entity = "Hello World"))
      } ~
      // 2) curl http://localhost:9000/another?name=john\&age=10 --> both parameters are required
      path("another") {
        // extract data from request: ex. parameter
        parameter("name", "age".as[Int]) {
          (name, age) =>
            val ageInTenYears = age + 10
            complete(s"Hello ${name}. You will be of age ${ageInTenYears} in ten years.")
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
