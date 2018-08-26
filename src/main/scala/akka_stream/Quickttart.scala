package akka_stream

import java.nio.file.Paths

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Flow, Keep, RunnableGraph, Sink, Source}
import akka.util.ByteString

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

// Reactive Streams:
// 1) Akka Streams implements the Reactive Streams specification
// 2) Reactive Streams' main goals:
//    i) backpressure
//   ii) async and non-blocking boundaries
//  iii) interoperability between different implementations

// 1) Akka Streams: a library to process and transfer a sequence of elements using "bounded" buffer space
//    a chain (stream/graph) of processing entities
//      each of these entities executes independently/concurrently
//      only buffering a limited number of elements at any given time
// 2) Akka Streams vs. Actor Model:
//    in Actor Model, each actor has an "unbounded"/"dropping" mailbox
//    in Akka Streams, processing entities have a "bounded" mailbox (never dropping)
//      it uses back-pressure to control the flow instead

object Quickttart extends App {
  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer() // an evaluation engine for the streams (note: akka streams are evaluated on top of actors)
  // "implicit" makes compiler be able to inject these dependencies automatically whenever they are needed

  // 1) Source:       a data creator, a Source is a description of the source you want to run, which can be transformed
  // 2) Flow:         a data connector used to transform elements, a Flow is a processing stage which has exactly one input and output
  //                  it connects its up- and downstreams by transforming the data elements flowing through it
  //    ex. if a Flow is connected to a Source, it results in a new Source
  //        if a Flow is connected to a Sink,   it results in a new Sink
  //        if a Flow is connected with both a Source and a Sink, it results in a RunnableFlow
  // 3) Sink:         a data consumer, a Sink is a set of stream processing steps that has one open input. it can be used as a Subscriber
  // 4) Materializer: a Materializer is a factory for stream execution engines, it is the thing that makes streams run
  //                    i.e. you need it for calling any of the run methods on a Source

  // example1: a simple source, emitting the integers 1 to 100
  val source: Source[Int, NotUsed] = Source(1 to 10)
  // note:
  // the second type: NotUsed signals that running the source produces some auxiliary value
  //   e.g. a network source may provide information about the bound port or the peer’s address
  // where no auxiliary information is produced, the type akka.NotUsed


  // 1) source.runForeach([func]) = runWith(Sink.foreach([func]))
  //    running this Source with a foreach procedure
  // 2) runWith():
  //    connecting this Source to a Sink and run it
  val done: Future[Done] = source.runForeach(num => println(num))(materializer)

  // example2: source.scan([initial value])([func]): like foldLeft()?
  //   use the scan operator to run a computation over the whole stream: starting with the number 1
  //   i.e. 1, 1 * 1, 1 * 1 * 2, 1 * 1 * 2 * 3 ...
  //        1 1 2 6 24 120 ... 3628800
  //   note: nothing is actually computed yet, this is a description of what we want to do once we run the stream
  val factorials = source.scan(BigInt(1))((acc, next) => acc * next)
  // def scan(zero: BigInt)(func: (BigInt, Out) => BigInt): Repr[BigInt]
  //   returns: type Repr[BigInt] = Flow[In, BigInt, Mat]


  // 3) FileIO.toPath([Path]):
  //      returns: Sink[ByteString, Future[IOResult]], note: Sink[-In, +Mat] where In: ByteString, Mat: Future[IOResult]]
  val matValue1: Future[IOResult] = // IOResult is what IO operations returns to tell you how many elements were consumed and whether the stream terminated normally
    factorials
      .map(num => ByteString(s"$num\n")) // transform the resulting series of numbers into a stream of ByteString objects
      .runWith(FileIO.toPath(Paths.get("factorials.txt"))) // the stream is then run by attaching a file (Sink) as the receiver of the data

  // example3:
  val tweets: Source[String, NotUsed] = Source("tweet1" :: "tweet2" :: Nil)
  val matValue2: Future[Done] = tweets
    .map(_.toUpperCase)     // Get all sets of tweets ...
    .reduce(_ ++ ", " ++ _) // reduce them to a single set
    .runWith(Sink.foreach(println)) // TWEET1, TWEET2: Attach the Flow to a Sink that will finally print the tweets

  // 4) RunnableFlow: a special form of a Flow, i.e. a stream that can be executed by just calling its run() method
  val src: Source[Int, NotUsed] = Source(1 to 3)                                     // Source[Int, NotUsed], where NotUsed is type of the materialized value
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](element => println(element)) // Sink[Int, Future[Done]], where Future[Done] is a future of type of the materialized value
  val flow: RunnableGraph[NotUsed] = src to sink // source.to([sink])                // RunnableGraph[NotUsed], where NotUsed is type of the materialized value
  val matValue3: NotUsed = flow.run() // 1 2 3                                       // NotUsed, where NotUsed is type of the materialized value (what we get when we run a stream: ex. side effects)

  // 5) Sink as an actor (vs. simple use case: ex. Sink as a Function1)
  val sinkActor = system.actorOf(Props(
    new Actor {
      override def receive = {
          case msg => println(s"sinkActor received: $msg")
      }
    }
  ))
  val sinkToActor = Sink.actorRef[Int](sinkActor, onCompleteMessage = "complete")
  val runnable1 = Source(1 to 3) to sinkToActor
  val matValue4: NotUsed = runnable1.run() // sinkActor received: 1, 2, 3, complete

  // 6) Flow
  val invertFlow = Flow[Int].map(element => element * -1)
  val runnable2 = Source(1 to 3) via invertFlow to Sink.foreach(element => println(element))
  // source.via([Flow]): the via() method connects a Source with a Flow, resulting a new Source
  val matValue5: NotUsed = runnable2.run() // -1 -2 -3

  // 7) materialized value:
  //    after running (materializing) the RunnableGraph[T] we get back the materialized value of type T
  //    every stream processing stage can produce a materialized value
  val sumSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)
  val runnable3: RunnableGraph[Future[Int]] = Source(1 to 10).toMat(sumSink)(Keep.right) // we are interested in the materialized value of sink (not source)
  // deafult is Keep.left: i.e. val runnable3: RunnableGraph[NotUsed] = Source(1 to 10).to(sumSink)
  val matValue6: Future[Int] = runnable3.run() // materialize the stream, resulting in the materialized value of sink
  println(Await.result(matValue6, 1.seconds))  // 1 + 2 + .... + 10 = 55
  // a stream can expose multiple materialized values, but it is quite common to be interested in:
  // i) only the value of the Source in the stream: ex. NotUsed or
  //ii) only the value of the Sink in the stream:   ex. Future[Done], Future[Int], etc.

  // a stream can be materialized multiple times, which are new for each such materialization
  val matValue7: Future[Int] = runnable3.run() // materialize the stream, resulting in the materialized value of sink
  println(Await.result(matValue7, 1.seconds))  // 1 + 2 + .... + 10 = 55
  // note: matValue6 and matValue7 are different futures

  // 8) other Source methods
  val source1: Source[String, NotUsed] = Source.single("single value")
  val source2: Source[String, NotUsed] = Source.fromFuture(Future.successful("success value")) // create a source from a Future

  system.terminate()
}
