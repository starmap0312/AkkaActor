package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source, SubFlow}

import scala.concurrent.Future

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

  val source2: Source[Int, NotUsed] = Source(1 to 10).groupBy(3, _ % 3).mergeSubstreams

  // Flattening operators
  // 2) flatMapConcat():
  val source3: Source[Int, NotUsed] = Source(1 to 2).flatMapConcat(i => Source(List.fill(3)(i)))
  val maValue3: Future[Done] = source3.via(Flow[Int].map({ x => println(s"$x"); x })).runWith(Sink.ignore) // 1 1 1 2 2 2
}
