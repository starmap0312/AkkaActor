package akka_http.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success}

class Scaffolding {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  val request: HttpRequest = HttpRequest(uri = "http://localhost:9000/chunked")

  def runWebService(route: Route): Unit = {
    val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandle(route, "localhost", 9001)
    bindingFuture onComplete {
      case Success(binding) => println(s"Server is listening on port: ${binding.localAddress.getPort}")
      case Failure(ex) => println(s"Binding fails with error: ${ex.getMessage}")
    }
    StdIn.readLine()
    system.terminate()
  }
}
