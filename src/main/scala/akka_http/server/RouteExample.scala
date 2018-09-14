package akka_http.server

package akka_http.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.io.StdIn
import scala.util.{Failure, Success}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{DateTime, headers}

object RouteExample extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  // Routing
  val route: Route =
    // Directives: ex. get, path for matching (filtering) requests
    get {                       // i.e. HttpMethods.GET
      // 1) curl http://localhost:9000/abc/10
      path("abc" / IntNumber) {        // i.e. Uri.Path("/abc")
        // leaf Directive: ex. complete, redirect, for specifying responses
        num => complete(s"Hello World, with num=${num}") // i.e. Future.successful(HttpResponse(entity = "Hello World"))
      } ~ // alternatively, take another path
      // 2) curl http://localhost:9000/another?name=john\&age=10
      path("another") {
        // extract data from request: ex. parameter
        parameter("name", "age".as[Int].?) { // parameter name is required, and age is optional
          (name: String, age: Option[Int]) =>
            val ageInTenYears = age.map(_ + 10).getOrElse(0)
            complete(s"Hello ${name}. You will be of age ${ageInTenYears} in ten years.")
        } ~ // alternatively, take only parameter age
        // 3) curl http://localhost:9000/another?age=10 -v
        parameter("age".as[Int]) {
          (age: Int) =>
            val ageInTenYears = age + 10
            // Directive that changes HttpResponse, ex. add header
            //   response header: "Last-Modified: Fri, 14 Sep 2018 11:44:00 GMT"
            respondWithHeader(headers.`Last-Modified`(DateTime.now)) {
              complete(s"Only age ${ageInTenYears} in ten years.")
            }
        }
      }
    } ~
    // 4) curl -X PUT http://localhost:9000/putty
    (put & path("putty")) {
      complete("put request")
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
