package akka_spray_json

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Random, Success}

case class ServerLog(ip: String, ctype: Int)

object CsvStreaming extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // provide a marshaller from ServerLog to ByteString:
  implicit val logAsCsv = Marshaller.strict[ServerLog, ByteString] { log =>
    Marshalling.WithFixedContentType(
      ContentTypes.`text/csv(UTF-8)`, // this type makes browser to open a file
      //ContentTypes.`text/plain(UTF-8)`, // this type makes browser to download a file
      () => ByteString(List(log.ip, log.ctype).mkString(","))
    )
  }
  // enable csv streaming:
  implicit val csvStreaming = EntityStreamingSupport.csv()

  def getLogs: Source[ServerLog, NotUsed] = Source.fromIterator(
    () => Iterator.fill(10) {
      var id = Random.nextInt()
      id = if (id < 0) -id else id
      ServerLog(s"140.112.1.${id % 10}", id % 10)
    }
  )

  val route = {
    pathSingleSlash {
      // marshal a stream of entities (ServerLog) into a CSV ByteString
      val serverlogs: Source[ServerLog, NotUsed] = getLogs
      complete(serverlogs)
    }
    // curl http://localhost:9001/
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
