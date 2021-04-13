package akka_stream

import akka.NotUsed

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, DelayOverflowStrategy, KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}

import scala.concurrent.{Await, Future}

// Dynamic stream handling: https://doc.akka.io/docs/akka/current/stream/stream-dynamic.html
object DynamicStreamHandling extends App {
  implicit val system = ActorSystem("StreamHandling")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer() // an evaluation engine for the streams (note: akka streams are evaluated on top of actors)

  // 1) UniqueKillSwitch.shutdown()
  //    this allows to control the completion of "one" materialized Graph of FlowShape
  val source1: Source[Int, NotUsed] = Source(Stream.from(1)) // create an infinite stream that starts from 1, 2, 3, 4, ...
  val sink1: Sink[Int, Future[Int]] = Sink.last[Int]

  val (killSwitch1, future1) = source1 // Source[Int, NotUsed]
    .delay(1.second, DelayOverflowStrategy.backpressure) // shifts elements emission by 1 second
    .viaMat(KillSwitches.single)(Keep.right) // Source[Int, UniqueKillSwitch]: KillSwitches.single materializes an external switch that allows external completion
    .toMat(sink1)(Keep.both) // RunnableGraph[(UniqueKillSwitch, Future[Int])]
    .run()

  Thread.sleep(2000) // doing something else

  killSwitch1.shutdown()
  // this completes its downstream and cancel its upstream (unless if finished or failed already)

  println(Await.result(future1, 1.second)) // this prints out the last number processed, ex. 16

  // 2) UniqueKillSwitch.abort()
  val source2 = Source(Stream.from(1))
  val sink2 = Sink.last[Int]

  val (killSwitch2, future2) = source2
    .delay(1.second, DelayOverflowStrategy.backpressure)
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(sink2)(Keep.both).run()

  Thread.sleep(2000) // doing something else

  killSwitch2.abort(new RuntimeException("boom!"))
  // this fails its downstream with the provided exception and cancel its upstream (unless if finished or failed already)

  println(Await.result(future2.failed, 1.second)) // java.lang.RuntimeException: boom!

}
