package akka_http.server

import akka.NotUsed
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{complete, get}
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka_http.client.Scaffolding

import scala.concurrent.duration._
import scala.util.Random

object MockServerFromIterator extends Scaffolding with App {

  // 1) fromIterator: stream the values from an Iterator, requesting the next value when there is demand
  //     def fromIterator[T](f: () ⇒ Iterator[T]): Source[T, NotUsed]
  val iterator: Iterator[String] = new Iterator[String] {
    val rand = new Random
    def hasNext = true
    def next = {
      var num1 = rand.nextInt
      var num2 = rand.nextInt
      var num3 = rand.nextInt
      num1 = if (num1 > 0) num1 else -num1
      num2 = if (num2 > 0) num2 else -num2
      num3 = if (num2 > 0) num2 else -num3
      s"140.112.${num1 % 255}.${num2 % 255},Type${num3 % 10}"
    }
  }
  val source: Source[String, NotUsed] =
    Source.fromIterator(() => iterator).
      throttle(1, 1.seconds, 1, ThrottleMode.Shaping)
      // slow down the stream to 1 element per second

  implicit val port: Int = 9000
  runWebService {
    get {
        complete {
          HttpResponse(
            entity = HttpEntity.Chunked.fromData(
              ContentTypes.`text/plain(UTF-8)`,
              source.map(line => ByteString(line + "\n", "UTF8"))
            )
          )
        }
    }
  }
}
