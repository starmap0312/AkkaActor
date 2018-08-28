package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka_stream.Quickttart.system

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

// Source.queue:
// materialize a SourceQueue onto which elements can be pushed for emitting from the source
object AkkaSourceQueue extends App {
  implicit val system = ActorSystem("AkkaSourceQueue")
  implicit val materializer = ActorMaterializer() // an evaluation engine for the streams (note: akka streams are evaluated on top of actors)

  val sourceQueue: SourceQueueWithComplete[Int] = Source.queue[Int](5, OverflowStrategy.backpressure) // Source[Int, SourceQueueWithComplete[Int]]
    .map(x => x * x)                                                        // Source[Int, SourceQueueWithComplete[Int]]
    .toMat(Sink.foreach(x => println(s"completed $x")))(Keep.left)          // RunnableGraph[SourceQueueWithComplete[Int]]
    .run()                                                                  // SourceQueueWithComplete[Int]

  implicit val dispatcher = system.dispatcher
  // 1) source.mapAsync():
  //    transform this stream by applying the given function to each of the element as they pass through this processing step
  val source: Source[Unit, NotUsed] = Source(1 to 10).mapAsync(1) {
    element => { // the mapping function should return a Future
      sourceQueue.offer(element).map {
        case QueueOfferResult.Enqueued    => println(s"enqueued $element")
        case QueueOfferResult.Dropped     => println(s"dropped $element")
        case QueueOfferResult.Failure(ex) => println(s"Offer failed ${ex.getMessage}")
        case QueueOfferResult.QueueClosed => println("Source Queue closed")
      }
    }
  }
  val matValue: Future[Done] = source.runWith(Sink.ignore) // runWith(): connecting this Source to a Sink and run it
  Await.result(matValue, 3.seconds)

  system.terminate()
}
