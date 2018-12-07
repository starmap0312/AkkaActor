package akka_stream

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source, SourceQueueWithComplete}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Success

// https://doc.akka.io/docs/akka/2.5/stream/operators/Source/queue.html
object SourceQueueExample extends App {
  implicit val system = ActorSystem("SourceQueueExample")
  implicit val materializer = ActorMaterializer()

  // 1) Source.queue[Int]([bufferSize], [overflowStrategy]): creates a Source of Int, and when it is run we get an auxiliary SourceQueue
  val source: Source[Int, SourceQueueWithComplete[Int]] =
    Source.queue[Int](bufferSize = 5, overflowStrategy = OverflowStrategy.backpressure)
      .throttle(elements = 3, 1.seconds) // control the rate of the Source to be: at most 3 elements per second
  val queue: SourceQueueWithComplete[Int] = source                     // connect the Source to a Sink that prints the number
    .toMat(Sink.foreach(num => println(s"completed $num")))(Keep.left) // keep the Source's materialized value, i.e. SourceQueue
    .run()(materializer) // run the source (stream): this returns the auxiliary SourceQueue

  implicit val dispatcher = system.dispatcher
  val anotherSource = Source(1 to 10) // creates another Source of Int
    .mapAsync(1)(num =>     // map each element of the Source to offer to the SourceQueue
      // def offer(elem: T): Future[QueueOfferResult], used to check if the enqueue is successful
      queue.offer(num).map {
        case QueueOfferResult.Enqueued    => println(s"enqueued $num")
        case QueueOfferResult.Dropped     => println(s"dropped $num")
        case QueueOfferResult.Failure(ex) => println(s"Offer failed ${ex.getMessage}")
        case QueueOfferResult.QueueClosed => println("Source Queue closed")
      }
    )
  val matValue2: Future[Done] = anotherSource.runWith(Sink.ignore)(materializer) // run the Source

  matValue2 onComplete {
    case Success(Done) => println("Running anotherSource is Done") // Running anotherSource is Done
  }

  StdIn.readLine()
  system.terminate()
}
