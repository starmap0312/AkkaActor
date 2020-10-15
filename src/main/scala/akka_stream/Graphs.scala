package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}

import scala.concurrent.Future

// https://doc.akka.io/docs/akka/current/stream/stream-graphs.html#constructing-graphs
object Graphs extends App {
  implicit val system = ActorSystem("Graphs")

  val runnable: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._
    val in: Source[Int, NotUsed] = Source(1 to 10)
    val out: Sink[Int, Future[Done]] = Sink.foreach[Int](x => println(x)) // 1000, 1000, 2000, 2000, ..., 10000, 10000

    val f1, f2, f3, f4 = Flow[Int].map(_ * 10) // Flow[Int, Int, NotUsed]

    val bcast = builder.add(Broadcast[Int](2))
    val merge = builder.add(Merge[Int](2))

    in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
    bcast ~> f4 ~> merge
    ClosedShape
  })

  val matValue: NotUsed = runnable.run()
}
