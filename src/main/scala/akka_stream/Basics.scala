package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

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
  val source2 = Source(1 to 10)
  val sink2: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)
  val sum2: Future[Int] = source.runWith(sink) // materialize the flow, getting the Sinks materialized value
  println(Await.result(sum, 1.seconds)) // 55

  // 3) operators: the building blocks of a Graph, ex. map(), filter(), or custom GraphStage
  //    operators are immutable, connecting them returns a new operator
  val source3 = Source(1 to 10)
  source3.map(_ => 0) // map each element to 0, but this has no effect on source, since it's immutable
  source3.runWith(Sink.fold(0)(_ + _)) // 55

  val zeroes = source.map(_ => 0) // returns new Source[Int], with `map()` appended
  zeroes.runWith(Sink.fold(0)(_ + _)) // 0

  // 4) a stream can be run(), materialized, multiple times
  //    a materialized value is calculated anew for each time
  // connect the Source to the Sink, obtaining a RunnableGraph
  val source4 = Source(1 to 10)
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
}
