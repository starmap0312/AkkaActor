package akka_stream

import akka.{Done, NotUsed}

import scala.concurrent.duration._
import akka.actor.{ActorSystem, Cancellable}
import akka.stream.{ActorMaterializer, DelayOverflowStrategy, KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, RunnableGraph, Sink, Source}

import scala.concurrent.{Await, Future}

// Dynamic stream handling: https://doc.akka.io/docs/akka/current/stream/stream-dynamic.html
object DynamicStreamHandling extends App {
  implicit val system = ActorSystem("StreamHandling")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer() // an evaluation engine for the streams (note: akka streams are evaluated on top of actors)

  // I. Controlling stream completion with KillSwitch:
  //    A KillSwitch allows the completion of operators of FlowShape from the outside
  // 1) UniqueKillSwitch.shutdown()
  //    this completes the stream via shutdown()
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
  //    this fails the stream via abort([exception])
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

  // II. Dynamic fan-in and fan-out with MergeHub, BroadcastHub and PartitionHub:
  //     when consumers or producers of a certain service (ex. a Sink, Source, or Flow) are dynamic and not known in advance

  // 1) MergeHub:

  // a simple consumer that will print to the console for now
  val consumer1: Sink[Any, Future[Done]] = Sink.foreach(println)
  // Hub!
  // Hello!

  // attach a MergeHub Source to the consumer: this will materialize to a corresponding Sink
  val runnableGraph1: RunnableGraph[Sink[String, NotUsed]] = MergeHub.source[String](perProducerBufferSize = 16).to(consumer1)

  // by running/materializing the consumer we get back `a corresponding Sink`, instead of a NotUsed/Future[Done]
  // this Sink can be materialized any number of times, but every element that enters the Sink will be consumed by this single consumer
  // note that as the consumer has been started, it means that there is a single sink that is attached to multiple producers
  val toConsumer1: Sink[String, NotUsed] = runnableGraph1.run()

  // feeding two independent sources into the hub
  val producerA: Source[String, NotUsed] = Source.single("Hello!")
  val producerB: Source[String, NotUsed] = Source.single("Hub!")
  producerA.runWith(toConsumer1)
  producerB.runWith(toConsumer1)

  // 2) BroadcastHub:
  //    it is used to consume elements from `a common producer` by `a dynamic set of consumers`

  // a simple producer that publishes a new "message" every second
  val producer2: Source[String, Cancellable] = Source.tick(1.second, 1.second, "New message")

  // by attaching a BroadcastHub Sink to the producer, it will materialize to `a corresponding Source`, instead of a Future[Done]
  // (We need to use toMat and Keep.right since by default the materialized value of the left is used)
  // this Source can be materialized any number of times, but every element that leaves the Source will be produced by this single producer
  val runnableGraph2: RunnableGraph[(Cancellable, Source[String, NotUsed])] = producer2.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)

  // Consumers can only be attached once the BroadcastHub.sink has been materialized
  // note that: as the producer has been started, it means that there is a single source that is attached to multiple consumers
  val (mat: Cancellable, fromProducer2: Source[String, NotUsed]) = runnableGraph2.run()
  // by running/materializing the producer, we get back a Source, which gives us access to the elements published by the producer
  // the resulting Source can be materialized any number of times, each materialization effectively attaching a new consumer
  // if there are no consumers attached to this hub, it will not drop any elements but instead backpressure the upstream producer until consumers arrive

  // consumed from the hub by two independent sinks
  val consumerA: Sink[Any, Future[Done]] = Sink.foreach(msg => println("consumer1: " + msg))
  val consumerB: Sink[Any, Future[Done]] = Sink.foreach(msg => println("consumer2: " + msg))
  fromProducer2.to(consumerA).run
  fromProducer2.to(consumerB).run
  // this prints out messages from the producer in two independent consumers
  //   consumer1: New message
  //   consumer2: New message
  //   consumer1: New message
  //   consumer2: New message
  //   ...
}
