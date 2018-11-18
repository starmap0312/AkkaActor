package akka_http.client

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Framing, Keep, Sink, Source}
import akka.util.ByteString
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.concurrent.Future

// data model for log line (ex. 140.112.23.2\ttype4)
case class RepoAccess2(ip: String, ctype: Int, port: Option[Int]) {
  def withPort(p: Int) = copy(port = Some(p))
}
object RepoAccess2 {
  val LogLineFormat = """(.+),Type(\d+)""".r // regular expression: scala.util.matching.Regex
  val fromLine: (String => Option[RepoAccess2]) = {
    case line @ LogLineFormat(ip, ctype) =>
      Some(RepoAccess2(ip, ctype.toInt, None))
    case line =>
      println(s"unknown format:${line}")
      None //unknown format
  }
}

object Step4WebSocket extends Scaffolding with App {
  val request: HttpRequest = HttpRequest(uri = "http://localhost:9000/")

  val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  def sourceFuture: Future[Source[String, Any]] = {
    Http().singleRequest(request).map { response =>
      response.entity.dataBytes. // Source[ByteString, Any]
        via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true)).
        map(_.utf8String).       // Source[String, Any]
        mapConcat(line => RepoAccess2.fromLine(line).toList). // similar to flatMap: map each line String to an Option.toList
        scan[Map[String, Int]](Map.empty)(updateMap). // map the String stream to a Map[String, Int] stream
        map(mp => mapper.writeValueAsString(mp))
      //map(_.toVector.sortBy(-_._2)) // map the Map[String, Int] to a sorted Vector[String, Int] stream
    }
  }

  def updateMap(mp: Map[String, Int], repo: RepoAccess2): Map[String, Int] = { // update & return a new map
    mp.updated(s"type${repo.ctype}", mp.getOrElse(s"type${repo.ctype}", 0) + 1) // update the Map by incrementing the value count
  }

  implicit val port: Int = 9001
  runWebService {
    get {
      pathSingleSlash {
        getFromResource("group-counts-table.html")
      } ~
      path("group-counts") {
        onSuccess(sourceFuture) { source =>
          val outStream = source
            .map(ws.TextMessage(_))
          val flow = Flow.fromSinkAndSource(Sink.ignore, outStream)
          handleWebSocketMessages(flow) // returns a Route
        }
      }
    }
  }
}
