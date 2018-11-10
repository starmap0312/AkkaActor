package akka_http.client

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Framing, Source}
import akka.util.ByteString
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.concurrent.Future

object Step3Aggregate extends Scaffolding with App {
  val request: HttpRequest = HttpRequest(uri = "http://localhost:9000/")

  val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  def sourceFuture: Future[Source[Array[Byte], Any]] = {
    Http().singleRequest(request).map { response =>
      response.entity.dataBytes. // Source[ByteString, Any]
        via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true)).
        map(_.utf8String).       // Source[String, Any]
        scan[Map[String, Int]](Map.empty)(updateMap). // map the String stream to a Map[String, Int] stream
        //map(_.toString())
        map(mp => mapper.writeValueAsBytes(mp))
        //map(_.toVector.sortBy(-_._2)) // map the Map[String, Int] to a sorted Vector[String, Int] stream
    }
  }

  def updateMap(mp: Map[String, Int], line: String): Map[String, Int] = {
    val Array(address, ctype) = line.split(',')
    println(address, ctype)
    mp.updated(ctype, mp.getOrElse(ctype, 0) + 1) // update the Map by incrementing the value count
  }

  implicit val port: Int = 9001
  runWebService {
    get {
      onSuccess(sourceFuture) { source =>
        complete {
          HttpResponse(
            entity = HttpEntity.Chunked.fromData(
              ContentTypes.`text/plain(UTF-8)`,
              source.map(arr => ByteString.fromArray(arr) ++ ByteString("\n"))
            )
          )
        }
      }
    }
  }
}
