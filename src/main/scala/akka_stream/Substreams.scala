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
// Substream:
// 1) it is represented as SubFlow (SubSource) instances, on which you can multiplex a single Source or Flow into a stream of streams
// 2) it cannot contribute to the super-flow’s materialized value since they are materialized later, during the runtime of the stream processing
object Substreams extends App {
  implicit val system = ActorSystem("Substreams")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()
  // Nesting operators:
  // 1) groupBy(maxSubstream, keyFn): demultiplexes the incoming stream into separate output streams
  //    when a new key is encountered for the first time, a new substream is opened and subsequently fed with all elements belonging to that key
  //    note:
  //      if allowClosedSubstreamRecreation=false (default), the operator keeps track of all keys of streams that have already been closed, so an infinite number of keys may cause memory issues
  //      if allowClosedSubstreamRecreation=true, substream completion and incoming elements are subject to race-conditions, ex. if elements arrive for a stream that is in the process of closing these elements might get lost
  //    it returns a SubFlow: after this operator all transformations are applied to all substreams in the same fashion.
  //      the substream is exited either by closing the substream (i.e. connecting to a Sink) or by merging the substreams back together (ex. mergeSubstreams)
  val subflow1: SubFlow[Int, NotUsed, Source[Int, NotUsed]#Repr, Source[Int, NotUsed]#Closed] = Source(1 to 5).groupBy(3, _ % 3)
  println("groupBy to sink")
  val graph1: RunnableGraph[NotUsed] = subflow1.to(Sink.foreach(println(_)))
  // Subflow.to([Sink]): attach a Sink to each sub-flow, closing the overall Graph that is being constructed
  graph1.run() // all sub-flows are connected to the Sink are run, producing 1 3 2 4 5, in random order
  Thread.sleep(1000)

  println("groupBy and mergeSubstreams")
  val subflow2: subflow1.Repr[Int] = subflow1.via(Flow[Int].map(_ * 2))
  val flow2: Source[Int, NotUsed] = subflow2.mergeSubstreams
  flow2.to(Sink.foreach(println(_))).run() // merge all subflows into a single source producing 3 * 2, 2 * 2, 1 * 2, 4 * 2, 5 *2, in random order
  // Flatten the sub-flows back into the super-flow by performing a merge without parallelism limit (i.e. having an unbounded number of sub-flows active concurrently)
  //   only up to Integer.MAX_VALUE substreams will be executed at any given time (i.e. substreams not yet executed are also not materialized: back-pressure applied)

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
  subflow1.mergeSubstreamsWithParallelism(1).to(Sink.foreach(println(_))).run() // since the number of running (i.e. not yet completed) substreams is capped, so this causes deadlock and generates only 1
  // as parallelism=1, this means that only up to 1 substream will be executed at any given time
  Thread.sleep(1000)

  // 1.2) splitWhen([strategy])(predicate)
  //      only generate a new substream if the predicate returns true
  //      note: groupby generates a new substream by the key computed
  println("splitWhen to sink")
  val charList: List[Char] = "1st line\n2nd line\n3rd line\n".toList
  val source12: Source[Char, NotUsed] = Source(charList)
  val subflow12: SubFlow[Char, NotUsed, Source[Char, NotUsed]#Repr, Source[Char, NotUsed]#Closed] = source12.splitWhen { _ == '\n' }
  val charCount: NotUsed = subflow12
    .to(Sink.foreach(print)) // 1st line.\n2nd line.\n3rd line
    .run()
  Thread.sleep(1000)
  val mergedStream: Source[String, NotUsed] = subflow12.map(_.toString).reduce(_ + _).concatSubstreams
  mergedStream.runForeach(println(_)) // 1st line.\n, 2nd line.\n, 3rd line

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

  // 2.3) statefulMapConcat
  println("statefulMapConcat")
  val source25: Source[String, NotUsed] = Source("a" :: "b" :: "c" :: "d" :: Nil)
  val letterAndIndex = source25.statefulMapConcat { () =>
    var counter = 0L // shared state

    // we return the function that will be invoked for each element
    { element =>
      counter += 1
      (element, counter) :: Nil // we return an iterable with the single element
    }
  }
  letterAndIndex.runForeach(println)
  // (a,1)
  // (b,2)
  // (c,3)
  // (d,4)
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

  // 6) splitWhen & splitAfter: applies the given predicate to all incoming elements and emits them to a "stream of sub-streams"
  //    splitWhen: begin a new sub-stream with the current element if the predicate is true
  //    splitAfter: end the current substream when the predicate is true
  Thread.sleep(1000)
  println("splitWhen")
  Source(1 to 10)
    .map(elem => elem)
    .sliding(2) // "Vector(1, "2"), Vector(2, 3)", "Vector(3, "4"), Vector(4, 5)", ..., "Vector(7, "8"), Vector(8, 9)", "Vector(9, 10)"
    .splitWhen { elems =>
      val cur = elems.head
      val next = elems.last
      next % 2 == 0 // begin a new sub-stream at Vector(1, "2"), Vector(3, "4"), ..., Vector(7, "8") & lastly Vector(9, 10)
    }
    .fold(Seq.empty[Int])((seq, x) => seq ++ x)
    .concatSubstreams // List(1, 2, 2, 3), List(3, 4, 4, 5), ..., List(7, 8, 8, 9), List(97, 98, 98, 99)
    .to(Sink.foreach(println))
    .run()

  Thread.sleep(1000)
  println("splitAfter")
  Source(1 to 10)
    .map(elem => elem)
    .sliding(2) // "Vector(1, "2")", "Vector(2, 3), Vector(3, "4")", "Vector(4, 5), Vector(5, "6")", ..., "Vector(6, 7), Vector(7, "8")", "Vector(8, 9), Vector(9, "10")"
    .splitAfter { elems =>
      val cur = elems.head
      val next = elems.last
      next % 2 == 0 // end the current substream at Vector(1, 2), Vector(3, 4), Vector(5, 6), ...
    }
    .fold(Seq.empty[Int])((seq, x) => seq ++ x)
    .concatSubstreams // List(1, 2), List(2, 3, 3, 4), List(4, 5, 5, 6), ..., List(6, 7, 7, 8), List(8, 9, 9, 10)
    .to(Sink.foreach(println))
    .run()
}
