package akka_stream

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable}
import akka.stream.Attributes.LogLevels
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}

/* Basics and working with Flows:
 * - https://doc.akka.io/docs/akka/current/stream/stream-flows-and-basics.html
 */
object Basics extends App {
  implicit val system = ActorSystem("QuickStart")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()

  // 1) RunnableGraph:
  //    after a stream is properly constructed by having both a source and a sink, represented by a RunnableGraph, ready to be run()
  val source: Source[Int, NotUsed] = Source(1 to 10)
  val sink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)
  val runnable: RunnableGraph[Future[Int]] = source.toMat(sink)(Keep.right) // toMat(): connect the Source to the Sink, obtaining a RunnableGraph
  // toMat(): declare that we want to transform the materialized value of the source and sink
  // Keep.right: we are only interested in the materialized value of the sink
  val sum: Future[Int] = runnable.run() // run(): materialize the flow (ex. starting up actors, make data flowing through the stream) and get the value of the sink
  // After running (materializing) the RunnableGraph[Future[Int]] we get back the materialized value of type Future[Int]
  println(Await.result(sum, 1.seconds)) // 55

  // 2) [Source].runWith([Sink]):
  //    runWith(): a convenience method for connecting the Source to the Sink and materializing the flow (i.e. run it)
  val source2: Source[Int, NotUsed] = Source(1 to 10)
  val sink2: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)
  val sum2: Future[Int] = source.runWith(sink) // materialize the flow, getting the Sinks materialized value
  println(Await.result(sum, 1.seconds)) // 55

  // 3) operators: the building blocks of a Graph, ex. map(), filter(), or custom GraphStage
  //    operators are immutable, connecting them returns a new operator
  val source3: Source[Int, NotUsed] = Source(1 to 10)
  source3.map(_ => 0) // map each element to 0, but this has no effect on source, since it's immutable
  source3.runWith(Sink.fold(0)(_ + _)) // 55

  val zeroes: Source[Int, NotUsed] = source.map(_ => 0) // returns new Source[Int], with `map()` appended
  zeroes.runWith(Sink.fold(0)(_ + _)) // 0

  // 4) a stream can be run(), materialized, multiple times
  //    a materialized value is calculated anew for each time
  // connect the Source to the Sink, obtaining a RunnableGraph
  val source4: Source[Int, NotUsed] = Source(1 to 10)
  val sink4 = Sink.fold[Int, Int](0)(_ + _)
  val runnable4: RunnableGraph[Future[Int]] = source4.toMat(sink4)(Keep.right)

  val sumA: Future[Int] = runnable.run() // get materialized value
  val sumB: Future[Int] = runnable.run() // a new materialized value: sumA and sumB are different Futures!

  // 5) defining sources, sinks and flows
  val source5_1: Source[Int, NotUsed] = Source(List(1, 2, 3)) // Create a source from an Iterable, ex. List

  val source5_2: Source[String, NotUsed] = Source.future(Future.successful("a future value")) // Create a source from a Future

  val source5_3: Source[String, NotUsed] = Source.single("a single element") // Create a source from a single element

  val source5_4: Source[Nothing, NotUsed] = Source.empty // Create an empty source

  val sink5_1 = Sink.head // Create a Sink that returns a materialized value containing the first element of the stream
  println(Await.result(source5_1.runWith(Sink.head), 1.seconds)) // 1

  val sink5_2: Sink[Any, Future[Done]] = Sink.ignore // Create a Sink that "consumes" a stream without doing anything with the elements
  println(Await.result(source5_1.runWith(sink5_2), 1.seconds)) // Done

  val sink5_3: Sink[Int, Future[Done]] = Sink.foreach[Int](println(_)) // Create a Sink that executes a side-effecting call for every element of the stream
  println(Await.result(source5_1.runWith(sink5_3), 1.seconds)) // Done, with side effect of printing out 1, 2, 3

  // 6) wiring up a Source, Sink and Flow
  val source6_1: Source[Int, NotUsed] = Source(1 to 3)
  val flow6_1: Flow[Int, Int, NotUsed] = Flow[Int].map(_ * 2)
  val sink6_1: Sink[Int, Future[Done]] = Sink.foreach(println(_))
  val runnable6_1: RunnableGraph[NotUsed] = source6_1.via(flow6_1).to(sink6_1)
  println(runnable6_1.run()) // NotUsed, with side effect of printing out 2, 4, 6

  // Starting from a Source
  val runnable6_2: RunnableGraph[NotUsed] = source6_1.to(sink6_1)
  println(runnable6_2.run()) // NotUsed, with side effect of printing out 1, 2, 3

  // Starting from a Sink
  val sink6_3: Sink[Int, NotUsed] = flow6_1.to(sink6_1)
  val runnable6_3: RunnableGraph[NotUsed] = source6_1.to(sink6_3)
  println(runnable6_3.run()) // NotUsed, with side effect of printing out 2, 4, 6

  // Broadcast to a sink inline
  val sink6_4: Sink[Int, Future[Done]] = Sink.foreach(x => println(x * 10))
  val otherSink: Sink[Int, NotUsed] = Flow[Int].alsoTo(sink6_1).alsoTo(sink6_4).to(Sink.ignore)
  val runnable6_4 = source6_1.to(otherSink)
  println(runnable6_4.run()) // NotUsed, with side effect of printing out 1, 10, 2, 20, 3, 30

  // 7) Back-pressure: a downstream Subscriber is able to receive and buffer (i.e. demand), and
  //    the upstream Publisher (i.e. Source in Akka Streams) guarantees that it will never emit more elements than the total demand
  // i) Slow Publisher, fast Subscriber: no back-pressure needed, but as the sending rate could change at any time, we may still need to safeguard
  //    i.e. push-mode: Publisher continues to produce elements as fast as it can, since the pending demand will be recovered just-in-time while it is emitting element
  //ii) Fast Publisher, slow Subscriber: back-pressure is needed, by applying one of the below strategies:
  //    a) do not generate elements: reduce upstream sending rate
  //    b) "buffer elements" in a bounded manner until more demand is signalled
  //    c) "drop elements" until more demand is signalled
  //    d) tear down the stream if unable to apply any of the above strategies
  //    i.e. pull-mode: pull the elements from the Publisher

  // 8) Stream Materialization
  //    a) constructing flows and graphs is like preparing a blueprint, an execution plan
  //    b) materialization is the process of allocating resources it needs in order to run, i.e. starting up Actors for the processing, ex. opening files or socket connections etc.
  //    c) materialization is triggered by the "terminal operations", ex. run() and runWith() methods defined on Source and Flow, or by syntactic sugars, ex. runForeach(el => ...), which is alias of runWith(Sink.foreach(el => ...))
  //    d) materialization is performed synchronously by an ActorSystem global "Materializer"
  //    e) the actual stream processing is handled by actors started up during the streams materialization, running on the thread pools defaults to the ActorSystem "dispatcher"

  // 9) Operator Fusion: Akka Streams will fuse the stream operators, meaning the processing steps within fused operators are executed by one Actor
  //    a) passing elements between fused operators is fast (no asynchronous messaging overhead)
  //    b) fused operators do not run in parallel (up to one CPU core is used for each fused part)

  //10) Combining materialized values: every operator can provide a materialized value after being materialized
  //    it is necessary to express how these values should be composed to a final value when we plug these operators together

  // Insert a throttler Flow
  val source10_1: Source[Int, NotUsed] = Source(1 to 3)
  val throttler10_1: Flow[Int, Int, NotUsed] = Flow[Int].throttle(elements = 1, per = 1.second) // Create a flow that throttles elements to 1/second
  val sink10_1: Sink[Int, Future[Done]] = Sink.foreach(println(_))
  val runnable10_1: RunnableGraph[Future[Done]] = source10_1.via(throttler10_1).toMat(sink10_1)(Keep.right) // by default: the materialized value of the leftmost stage is preserved

  println(Await.result(runnable10_1.run(), 3.seconds)) // Done, need to wait for at least 3 seconds as there is a throttler

  // By default, the materialized value of the leftmost stage is preserved
  val source10_2: Source[Int, Promise[Option[Int]]] = Source.maybe[Int] // Create source that can be signalled explicitly from the outside
  val throttler10_2: Flow[Int, Int, NotUsed] = Flow[Int].throttle(elements = 1, per = 1.second) // Create a flow that throttles elements to 1/second
  val sink10_2: Sink[Int, Future[Int]] = Sink.head[Int] // Create a sink that returns the first element of a stream in the returned Future
  val runnable10_2: RunnableGraph[Promise[Option[Int]]] = source10_2.via(throttler10_2).to(sink10_2) // By default, the materialized value of the leftmost stage is preserved

  // Simple selection of materialized values by using Keep.right
  val source10_3: Source[Int, NotUsed] = source10_2.viaMat(throttler10_2)(Keep.right) // use right flows's materialized value
  val runnable10_3: RunnableGraph[NotUsed] = source10_3.to(sink10_2)

  val source10_4: Source[Int, Promise[Option[Int]]] = source10_2.via(throttler10_2) // left stage's materialized is preserved
  val runnable10_4: RunnableGraph[Future[Int]] = source10_4.toMat(sink10_2)(Keep.right) // use right flows's materialized value

  // Using runWith will always give the materialized values of the stages added by runWith() itself
  val source10_5: Source[Int, Promise[Option[Int]]] = source10_2.via(throttler10_2) // left stage's materialized is preserved
  val runnable10_5: Future[Int] = source10_5.runWith(sink10_2)  // use runWith's materialized value, i.e. sink10_2's materialized value

  val sink10_6: Sink[Int, NotUsed] = throttler10_2.to(sink10_2) // left stage's materialized is preserved
  val runnable10_6: Promise[Option[Int]] = sink10_6.runWith(source10_2) // use runWith's materialized value, i.e. source10_2's materialized value

  val runnable10_7: (Promise[Option[Int]], Future[Int]) = throttler10_2.runWith(source10_2, sink10_2) // use runWith's materialized value, i.e. source10_2's and sink10_2's materialized value

  // More complex  selection of materialized values by using Keep.both
  val source10_8: Source[Int, (Promise[Option[Int]], NotUsed)] = source10_2.viaMat(throttler10_2)(Keep.both) // use both flows' materialized values
  val runnable10_8: RunnableGraph[(Promise[Option[Int]], NotUsed)] = source10_8.to(sink10_2) // left stage's materialized is preserved

  val source10_9: Source[Int, (Promise[Option[Int]], NotUsed)] = source10_2.viaMat(throttler10_2)(Keep.both) // use both flows' materialized values
  val runnable10_9: RunnableGraph[((Promise[Option[Int]], NotUsed), Future[Int])] = source10_9.toMat(sink10_2)(Keep.both) // use both flows' materialized values


  // map over the materialized values, ex. in runnable10_9 we had a doubly nested pair, we can flatten it out as the following
  val runnable10_10: RunnableGraph[(Promise[Option[Int]], NotUsed, Future[Int])] = runnable10_9.mapMaterializedValue {
    case ((promise, notused), future) => (promise, notused, future)
  }
  // then we can use pattern matching to get the resulting materialized values
  val (promise, notused, future) = runnable10_10.run()

  // Type inference works as expected
  promise.success(None)
  future.map(_ + 3)

  // 11) Source pre-materialization: need a Source materialized value before the Source gets hooked up to the rest of the graph, ex. Source.queue, Source.actorRef or Source.maybe
  val completeWithDone: PartialFunction[Any, CompletionStrategy] = { case Done => CompletionStrategy.immediately }
  val actorPoweredSource: Source[String, ActorRef] = Source.actorRef[String](
    completionMatcher = completeWithDone,
    failureMatcher = PartialFunction.empty,
    bufferSize = 100,
    overflowStrategy = OverflowStrategy.fail
  ) // Create a "materialized-value powered Source", a Source of String powered by its materialized value, ActorRef, used to produce elements

  // as we need to materialize the stream in order to obtain the materialized value actorRef, but we do not want to run it now,
  // so we use preMaterialize() to obtain the materialized value and "another Source"
  val (actorRef, source_11): (ActorRef, Source[String, NotUsed]) = actorPoweredSource.preMaterialize()

  // the actor is allocated, so we could send it the messages, elements of String
  actorRef ! "Hello!"
  actorRef ! "World!"

  source_11.runWith(Sink.foreach(println)) // Hello! World!: run the "another Source" now

  // Stream ordering: Akka Streams almost all computation operators "preserve input order of elements"
  // i.e. if inputs {IA1, IA2, ..., IAn} "cause" outputs {OA1, OA2, ..., OAk} and
  //      if inputs {IB1, IB2, ..., IBm} "cause" outputs {OB1, OB2, ..., OBl} and
  //      if all of IAi happened before all IBi:
  //    then OAi happens before OBi

  // 12) Actor Materializer Lifecycle:
  //     Materializer: turn the stream blueprint into a running stream and emitting the "materialized value"
  //     a) When run by the system materializer the streams will run until the ActorSystem is shut down
  //     b) the usual way to terminate streams is to  cancel or complete them
  //     c) When the materializer is shut down before the streams have run to completion, they will be terminated abruptly
  final class RunWithMyself extends Actor {
    implicit val mat = Materializer(context) // the ActorContext is used to create the materializer: this binds the stream's lifecycle to the surrounding Actor

    val source: Source[Int, Promise[Option[Int]]] = Source.maybe[Int]
    val sink: Sink[Int, NotUsed] = Sink.onComplete {
      case Success(done) => println(s"Completed: $done")
      case Failure(ex)   => println(s"Failed: ${ex.getMessage}")
    }
    val matValue: NotUsed = source.runWith(sink)

    def receive = {
      case "boom" => context.stop(self) // if we stop the Actor it would terminate the stream as well
      // as it makes no sense to keep the stream alive when the actor has terminated already
      // if the streams are not terminated, will be signalled by an “Abrupt termination exception” signaled by the stream
    }
  }

  // explicitly create a stream that will out-last the actor’s life
  final class RunForever(implicit val mat: Materializer) extends Actor {
    // because we use Akka system's Materializer, the streams will not be terminated even if the actor is stopped
    // this is useful if you want to share a materializer or group streams into specific materializers, for example because of the materializer’s settings, etc

    val source: Source[Int, Promise[Option[Int]]] = Source.maybe[Int]
    val sink: Sink[Int, NotUsed] = Sink.onComplete {
      case Success(done) => println(s"Completed: $done")
      case Failure(ex)   => println(s"Failed: ${ex.getMessage}")
    }
    val matValue: NotUsed = source.runWith(sink)

    def receive = {
      case "boom" => context.stop(self) // will NOT terminate the stream, as it's bound to the system!
    }
  }

  // 13) Flow.log():
  //     Logs elements flowing through the stream as well as completion and erroring
  val log = LoggerFactory.getLogger(this.getClass)
  val verboseAttribute = Attributes(LogLevels(LogLevels.Info, LogLevels.Info, LogLevels.Info))

  val source13: Source[Int, NotUsed] = Source(1 to 3)
  val flow13: Flow[Int, Int, NotUsed] = Flow[Int].map(_ * 2)
  val sink13: Sink[Int, Future[Done]] = Sink.ignore
  val runnable13: RunnableGraph[NotUsed] = source13
    .via(flow13)
    .log("test12", x => s"${x}")
    .addAttributes(verboseAttribute)
    .to(sink13)
  // [INFO] [test12] Element: 2
  // [INFO] [test12] Element: 4
  // [INFO] [test12] Element: 6
  println(runnable13.run()) // NotUsed, with side effect of printing out 2, 4, 6

  // 14) Source.preMaterialize():
  //     Materializes this Source, immediately returning:
  //     (a) its materialized value (ex. SourceQueue), and
  //     (b) a new Source that can be used to consume elements from the newly materialized Source
  val (queue14: SourceQueueWithComplete[Int], source14: Source[Int, NotUsed]) = Source.queue[Int](3, OverflowStrategy.backpressure).preMaterialize()
  val matValue: Future[Done] =
    Source(1 to 3) // use another Int Source to offer elements to queue14
      .via(Flow[Int].map(x => {println(x); x})) // print 1, 2, 3
      .mapAsync(1)(num => queue14.offer(num * 2))
      .run()

  Thread.sleep(1000)
  source14.to(Sink.foreach(println(_))).run() // print 2, 4, 6, the preMaterialized source takes elements from the queue14.offer

  // 15) mapAsync: Future fails with Exception
  println("mapAsync example 1")
  val matValue15: Future[Done] = Source(1 to 3)
    .mapAsync(parallelism = 1) { x => if (x == 2) Future.failed(new Exception("Future fails in mapAsync")) else Future.successful(x) } // the stream completes with failure at value=2; value=3 will not be processed
    .runWith(Sink.foreach[Int](x => println(s"mapAsync: Future succeeds with value=${x}"))) // mapAsync: Future succeeds with value=1
  matValue15.onComplete {
    case Success(_) =>
    case Failure(ex) => println(s"matValue15 fails with ${ex}") // matValue15 fails with java.lang.Exception: Future fails in mapAsync
  }

  Thread.sleep(1000)
  println("mapAsync example 2")
  val matValue15_2: Future[Done] = Source(1 to 3)
    .mapAsync(parallelism = 1) { x => if (x == 2) Future.failed(new Exception("Future fails in mapAsync2")) else Future.successful(x) } // the stream completes successfully at value=2 (note: value=3 is not processed)
    .recover {
      case ex =>
        println("Recover allows to send last element on failure and gracefully complete the stream")
        2 // Recover allows to send last element on failure and gracefully complete the stream
    }
    .runWith(Sink.foreach[Int](x => println(s"mapAsync2: Future succeeds with value=${x}"))) // mapAsync2: Future succeeds with value=1, mapAsync2: Future succeeds with value=2 (note: value=3 is not processed)
  matValue15_2.onComplete {
    case Success(_) => println(s"matValue15_2 succeeds with recovery") // matValue15_2 succeeds with recovery
    case Failure(ex) =>
  }

  // 16) Source.tick([initialDelay], [interval], [tick data])
  val tickSource: Source[Symbol, Cancellable] = Source.tick(100.millis, 1.second, 'tick) // generate a tick data
  val handler: Cancellable = tickSource.to(Sink.foreach(println)).run
  Thread.sleep(3000)
  handler.cancel()

  // 17) Source.extrapolate/Flow.extrapolate
  //     this allows for a faster downstream by expanding the last emitted element to an Iterator
  //     ex. an Iterator.continually(element) will cause extrapolate to keep repeating the last emitted element
  val slowSource: Source[Int, Cancellable] = Source.tick(0.seconds, 10.seconds, 0) // a slow source that generates a tick every 10 seconds
  val repeatLastFlow: Flow[Int, Int, NotUsed] = Flow[Int].extrapolate(Iterator.continually(_))
  val rateControlSource: Source[Int, Cancellable] = slowSource.via(repeatLastFlow) // the slow source is combined with a flow that repeats its last element to allow faster consumption
  val fastSource: Source[Int, Cancellable] = Source.tick(0.seconds, 1.second, 1)
  val handler2: Cancellable = fastSource.zip(rateControlSource).to(Sink.foreach(println)).run // this prints out a (1,0) every second
  Thread.sleep(3000)
  handler2.cancel()

  val slowButRepeatLastSource = Source.tick(0.seconds, 10.seconds, 2).extrapolate(Iterator.continually(_))
  slowButRepeatLastSource.runForeach(println)

  Thread.sleep(1000)
  system.terminate()
}
