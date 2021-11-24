package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._

// Source.queue:
// materialize a SourceQueue onto which elements can be pushed for emitting from the source
object AkkaSourceQueue extends App {
  implicit val system = ActorSystem("AkkaSourceQueue")
  implicit val materializer = ActorMaterializer() // an evaluation engine for the streams (note: akka streams are evaluated on top of actors)

  // note: the upstream can push elements to the queue (i.e. enqueued) as long as the bufferSize=2 is not reached
  // note: as we adopt OverflowStrategy.backpressure, backpressure is propagated to the upstream when the queue is full
  //       ex. completed 1, enqueued 1, enqueued 2, enqueued 3, completed 2, enqueued 4, completed 3, enqueued 5
  // note: if we adopt OverflowStrategy.dropNew, new elements will be dropped when the queue is full
  //       ex. completed 1, enqueued 1, enqueued 2, enqueued 3, dropBoundedSourceQueueped 4, dropBoundedSourceQueueped 5
  val sourceQueue: SourceQueueWithComplete[Int] = Source.queue[Int](1, OverflowStrategy.dropNew) // Source[Int, SourceQueueWithComplete[Int]]
    .throttle(1, 1.second)
    .map(x => x)                                                   // Source[Int, SourceQueueWithComplete[Int]]
    .toMat(Sink.foreach(x => println(s"completed $x")))(Keep.left) // RunnableGraph[SourceQueueWithComplete[Int]]
    .run()
  // SourceQueueWithComplete[Int]: materialize the graph and run it, the return value is a SourceQueue

  implicit val dispatcher = system.dispatcher
  // 1) source.mapAsync():
  //    transform this stream by applying the given function to each of the element as they pass through this processing step
  val source: Source[String, NotUsed] = Source(1 to 5).mapAsync(1) { // create another source to feed elements into the queue
    element => { // the mapping function should return a Future
      sourceQueue.offer(element).map {
        case QueueOfferResult.Enqueued    => s"enqueued $element"
        case QueueOfferResult.Dropped     => s"dropBoundedSourceQueueped $element"
        case QueueOfferResult.Failure(ex) => s"Offer failed ${ex.getMessage}"
        case QueueOfferResult.QueueClosed => s"Source Queue closed"
      }
    }
  }
  val matValue: Future[Done] = source.runWith(Sink.foreach(println)) // runWith(): connecting this Source to a Sink and run it

  matValue onComplete {
    case Success(Done) => system.terminate()
    case Failure(_) => system.terminate()
  }
}
