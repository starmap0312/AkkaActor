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

  val route: Route =
    get {                       // i.e. HttpMethods.GET
      path("abc") {        // i.e. Uri.Path("/abc")
        complete("Hello World") // i.e. Future.successful(HttpResponse(entity = "Hello World"))
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
