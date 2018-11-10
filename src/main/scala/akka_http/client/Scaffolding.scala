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

  def runWebService(route: Route)(implicit port: Int = 9000): Unit = {
    val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandle(route, "localhost", port)
    // note: to enable remote access to your server, you need to bind your server to the external interface
    //       ex. to simply bind to all interfaces, you can set the host/IP to 0.0.0.0
    //       i.e. Http().bindAndHandle(route, "0.0.0.0", 9001)
    bindingFuture onComplete {
      case Success(binding) => println(s"Server is listening on port: ${binding.localAddress.getPort}")
      case Failure(ex) => println(s"Binding fails with error: ${ex.getMessage}")
    }
    StdIn.readLine()
    system.terminate()
  }
}
