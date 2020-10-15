package akka_stream

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Success

// https://doc.akka.io/docs/akka/2.5/stream/operators/Source/queue.html
object SourceQueueExample extends App {
  implicit val system = ActorSystem("SourceQueueExample")
  implicit val materializer = ActorMaterializer()

  // 1) Source.queue[Int]([bufferSize], [overflowStrategy]): creates a Source of Int, and when it is run we get an auxiliary SourceQueue
  //      we can then offer numbers to the Source using the auxiliary SourceQueue
  val source: Source[Int, SourceQueueWithComplete[Int]] =
    Source.queue[Int](bufferSize = 3, overflowStrategy = OverflowStrategy.backpressure)
      .throttle(elements = 1, 3.seconds) // control the rate of the Source to be: at most 1 elements per 3 seconds (a slow downstream)
  val queue: SourceQueueWithComplete[Int] = source                     // connect the Source to a Sink that prints the number
    .toMat(Sink.foreach(num => println(s"completed $num")))(Keep.left) // keep the Source's materialized value, i.e. SourceQueue
    .run()(materializer) // run the stream (graph) to get the auxiliary SourceQueue

  implicit val dispatcher = system.dispatcher
  val anotherSource = Source(1 to 5) // creates another Source of Int (a fast upstream)
    .mapAsync(1)(num =>     // make the Source to map its number to offer to the SourceQueue
      // def offer(elem: T): Future[QueueOfferResult], used to check if the enqueue is successful
      queue.offer(num).map {
        case QueueOfferResult.Enqueued    => println(s"enqueued $num")
        case QueueOfferResult.Dropped     => println(s"dropped $num")
        case QueueOfferResult.Failure(ex) => println(s"Offer failed ${ex.getMessage}")
        case QueueOfferResult.QueueClosed => println("Source Queue closed")
      }
    )
  val matValue2: Future[Done] = anotherSource.runWith(Sink.ignore)(materializer)
  // connect the Source to a Sink that does nothing, then run the stream to offer numbers to the SourceQueue
  // note: the stream will be back pressured by the slow downstream (SourceQueue)
  // i.e. enqueued 1 & completed 1, then enqueued 2, 3, 4; it cannot offer 5 as bufferSize = 3; it needs to wait for the completion of 2 so that it could offer (enqueue) 5

  matValue2 onComplete {
    case Success(Done) => println("anotherSource is Done with offering all its numbers") // anotherSource is Done when enqueued 5 (after completed 2)
  }

  // SourceQueue.watchCompletion
  queue watchCompletion() onComplete {
    case Success(Done) => println("SourceQueue is complete")
  }

  StdIn.readLine() // this is required: otherwise, we will terminate both streams abruptly (throw AbruptStageTerminationException exception)
  queue.complete() // SourceQueue is complete: watchCompletion catches the SourceQueue completion
  StdIn.readLine()
  system.terminate()
}
