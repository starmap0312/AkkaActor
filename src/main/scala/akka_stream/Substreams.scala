package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source, SubFlow}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

// ref: https://doc.akka.io/docs/akka/current/stream/stream-substream.html
object Substreams extends App {
  implicit val system = ActorSystem("Substreams")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()
  // Nesting operators:
  // 1) groupBy(): demultiplexes the incoming stream into separate output streams
  val source1: SubFlow[Int, NotUsed, Source[Int, NotUsed]#Repr, Source[Int, NotUsed]#Closed] = Source(1 to 10).groupBy(3, _ % 3)
  val runnable1: RunnableGraph[NotUsed] = Source(1 to 10).groupBy(3, _ % 3).to(Sink.ignore)
  val maValue1: NotUsed = runnable1.run()

  val source12: Source[Int, NotUsed] = Source(1 to 10).groupBy(3, _ % 3).mergeSubstreams

  // Flattening operators
  // 2) flatMapConcat():
  val source2: Source[Int, NotUsed] = Source(1 to 2).flatMapConcat(i => Source(List.fill(3)(i)))
  val maValue2: Future[Done] = source2.via(Flow[Int].map({ x => println(s"$x"); x })).runWith(Sink.ignore) // 1 1 1 2 2 2

  // Fan-out operators
  // 3) Source.divertTo(sink, predicate)/Flow.divertTo(sink, predicate):
  //    Each upstream element will either be diverted to the given sink, or the downstream consumer according to the predicate function applied to the element
  val source3: Source[Int, NotUsed] = Source(1 to 10).divertTo(Sink.foreach(x => println(s"diverted to sink: ${x}")), _ % 2 == 0) // diverted to sink: 2, 4, 6, 8, 10
  val maValue3: Future[Done] = source3.runWith(Sink.ignore)
  Await.result(maValue3, 2.second)

  // 4) Source.divertToMat(sink, predicate)(Keep.right):
  //    Each upstream element will either be diverted to the given sink, or the downstream consumer according to the predicate function applied to the element
  val source4: Source[Int, (NotUsed, Future[Done])] = Source(1 to 10).divertToMat(Sink.foreach(x => println(s"diverted to sink: ${x}")), _ % 2 == 0)(Keep.both) // diverted to sink: 2, 4, 6, 8, 10
  val maValue4: (NotUsed, Future[Done]) = source4.to(Sink.foreach(x => println(s"elements not diverted: ${x}"))).run() // elements not diverted: 1, 3, 5, 7, 9
  Await.result(maValue4._2, 2.second)
}
