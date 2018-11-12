package akka_http.client

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success}

object Step1StreamServer extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  def sourceFuture: Future[Source[String, Any]] = {
    val request: HttpRequest = HttpRequest(uri = "http://localhost:9000/")
    val responseFuture: Future[HttpResponse] = Http().singleRequest(request)
    val future: Future[Source[String, Any]] = responseFuture.map { response =>
      response.entity.dataBytes. // Source[ByteString, Any]
        map(_.utf8String) // Source[String, Any]
    }
    future
  }

  val route: Route =
    get {
      onSuccess(sourceFuture) { source =>
        complete {
          HttpResponse(
            entity = HttpEntity.Chunked.fromData(
              ContentTypes.`text/plain(UTF-8)`,
              source.map(line => ByteString(line, "UTF8"))
            )
          )
        }
      }
    }

  val config = system.settings.config.getConfig("app")
  val interface = config.getString("interface")
  val port = config.getInt("port")

  val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandle(route, interface, port)
  bindingFuture onComplete {
    case Success(binding) => println(s"Server is listening on port: ${binding.localAddress.getPort}")
    case Failure(ex) => println(s"Binding fails with error: ${ex.getMessage}")
  }
  StdIn.readLine()
  system.terminate()
}