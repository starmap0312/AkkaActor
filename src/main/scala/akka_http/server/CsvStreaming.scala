package akka_http.server

import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToResponseMarshallable}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{complete, onComplete, path}
import akka.stream.javadsl.Source
import akka.http.scaladsl.common.{ EntityStreamingSupport, JsonEntityStreamingSupport }
import akka.util.ByteString

case class ServerLog(ip: String, ctype: Int)

object CsvStreaming extends App {
  // provide a marshaller from ServerLog to ByteString:
  implicit val logAsCsv = Marshaller.strict[ServerLog, ByteString] { log =>
    Marshalling.WithFixedContentType(
      ContentTypes.`text/csv(UTF-8)`,
      () => ByteString(List(log.ip, log.ctype).mkString(","))
    )
  }
  // enable csv streaming:
  implicit val csvStreaming = EntityStreamingSupport.csv()

  val logs = List(ServerLog("140.112.1.1", 1), ServerLog("140.112.1.2", 2))
  def getLogs = Source.single(logs)

  val route = {
    path("tweets") {
      //val source: Source[ServerLog, NotUsed] = Source.single()
      //complete(ToResponseMarshallable(ServerLog("140.112.1.1", 4)))
      val serverlogs = getLogs
      //complete(serverlogs)
      complete("hello")
    }
  }
}
