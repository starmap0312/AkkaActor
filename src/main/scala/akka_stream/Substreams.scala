package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, SubstreamCancelStrategy}
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source, SubFlow}

import scala.collection.immutable
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

// ref: https://doc.akka.io/docs/akka/current/stream/stream-substream.html
// ref: https://doc.akka.io/docs/akka/current/stream/operators/index.html
object Substreams extends App {
  implicit val system = ActorSystem("Substreams")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()
  // Nesting operators:
  // 1) groupBy(maxSubstream, keyFn): demultiplexes the incoming stream into separate output streams
  val subflow1: SubFlow[Int, NotUsed, Source[Int, NotUsed]#Repr, Source[Int, NotUsed]#Closed] = Source(1 to 5).groupBy(3, _ % 3)
  println("groupBy to sink")
  subflow1.to(Sink.foreach(println(_))).run() // all subflows are connected to the Sink producing 1 3 2 4 5, in random order
  Thread.sleep(1000)

  println("groupBy and mergeSubstreams")
  subflow1.mergeSubstreams.to(Sink.foreach(println(_))).run() // merge all subflows into a single source producing 3 2 1 4 5, in random order
  Thread.sleep(1000)

  Source(1 to 10).groupBy(100, _ % 3).take(2).reduce((x, y) => x + y).mergeSubstreams.to(Sink.foreach(x => println(s"groupBy & take & reduce: ${x}"))).run()
  // 1, 4, 7, 10 => (take 2) => 1, 4 => (reduce) => 5 \\
  // 2, 5, 8 => (take 2) => 2, 5 => (reduce) => 7     ==> 5, 7, 9
  // 3, 6, 9 => (take 2)  => 3, 6 => (reduce) => 9    //
  Thread.sleep(1000)
  Source(1 to 10).groupBy(100, x => s"${x % 3} xxx").take(2).reduce((x, y) => x + y).mergeSubstreams.to(Sink.foreach(x => println(s"groupBy & take & reduce: ${x}"))).run()
  // 1, 4, 7, 10 => (take 2) => 1, 4 => (reduce) => 5 \\
  // 2, 5, 8 => (take 2) => 2, 5 => (reduce) => 7     ==> 5, 7, 9
  // 3, 6, 9 => (take 2)  => 3, 6 => (reduce) => 9    //
  Thread.sleep(1000)

  // 1.1) concatSubstreams(): you limit the number of active substreams running and being merged at a time, with the concatSubstreams method
  //      concatSubstreams() is equivalent to mergeSubstreamsWithParallelism(1)
  println("groupBy and concatSubstreams")
  subflow1.concatSubstreams.to(Sink.foreach(println(_))).run() // since the number of running (i.e. not yet completed) substreams is capped, so this causes deadlock and generates only 1
  Thread.sleep(1000)

  // 1.2) splitWhen([strategy])(predicate)
  //      only generate a new substream if the predicate returns true
  //      note: groupby generates a new substream by the key computed
  println("splitWhen to sink")
  val charList: List[Char] = "1st line\n2nd line\n3rd line\n".toList
  val subflow12: SubFlow[Char, NotUsed, Source[Char, NotUsed]#Repr, Source[Char, NotUsed]#Closed] = Source(charList).splitWhen { _ == '\n' }
  val charCount = subflow12
    .to(Sink.foreach(print)) // 1st line.\n2nd line.\n3rd line
    .run()
  Thread.sleep(1000)

  // Flattening operators
  // 2) Source/Flow.flatMapConcat():
  //    Transform each input element into a Source whose elements are then flattened into the output stream through concatenation
  println("flatMapConcat")
  val source2: Source[Int, NotUsed] = Source(1 to 2).flatMapConcat(i => Source(List.fill(3)(i)))
  source2.runWith(Sink.foreach(println(_))) // 1 1 1 2 2 2
  Thread.sleep(1000)

  // 2.2) Source/Flow.flatMapMerge(breadth, fn: (_ => Source))
  //    Transform each input element into a Source whose elements are then flattened into the output stream through merging
  //    where at most `breadth` substreams are being consumed at any given time
  println("flatMapMerge: breadth=1")
  val source22: Source[Int, NotUsed] = Source(1 to 2).flatMapMerge(1, i => Source(List.fill(3)(i)))
  source22.runWith(Sink.foreach(println(_))) // 1 1 1 2 2 2
  Thread.sleep(1000)
  println("flatMapMerge: breadth=2")
  val source23: Source[Int, NotUsed] = Source(1 to 2).flatMapMerge(2, i => Source(List.fill(3)(i)))
  source23.runWith(Sink.foreach(println(_))) // 2 1 2 1 2 1
  Thread.sleep(1000)
  println("flatMapMerge: breadth=3")
  val source24: Source[Int, NotUsed] = Source(1 to 2).flatMapMerge(3, i => Source(List.fill(3)(i)))
  source24.runWith(Sink.foreach(println(_))) // 2 1 2 1 2 1
  Thread.sleep(1000)

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

  // 5) Source.alsoTo(sink):
  //    send elements that pass through this Flow also to another Sink
  //    note: if the wireTap Sink backpressures, elements that would've been sent to it will be "backpressured"
  val source5: Source[Int, NotUsed] = Source(1 to 5).alsoTo(Sink.foreach(x => println(s"alsoTo to sink: ${x}"))) // alsoTo to sink: 1, 2, 3, 4, 5
  val maValue5 = source5.to(Sink.foreach(x => println(s"elements not alsoTo: ${x}"))).run() // elements not alsoTo: 1, 2, 3, 4, 5

  // 5) Source.wireTap(sink):
  //    elements that pass through this Flow will also be sent to the wire-tap Sink, without affecting the mainline flow
  //    If the wireTap Sink backpressures, elements that would've been sent to it will be "dropped" instead
  val source6 = Source(1 to 5).wireTap(Sink.foreach[Int](x => println(s"wireTap to sink: ${x}")))  // elements wireTap to sink: 1, 2, 3, 4, 5
  val maValue6 = source6.to(Sink.foreach(x => println(s"elements not wireTap: ${x}"))).run() // elements not wireTap: 1, 2, 3, 4, 5

}
