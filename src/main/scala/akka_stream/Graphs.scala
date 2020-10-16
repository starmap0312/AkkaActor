package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source}
import akka_stream.GraphStages.system

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

// https://doc.akka.io/docs/akka/current/stream/stream-graphs.html#constructing-graphs
object Graphs extends App {
  implicit val system = ActorSystem("Graphs")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()

  // 1) construct a runnable graph with Broadcast & Merge:
  //    Broadcast: Fan-out the stream to several streams emitting each incoming upstream element to all downstream consumers
  //    Merge: Merge several streams, taking elements as they arrive from input streams,
  //           picking randomly when several have elements ready
  val graph1: Graph[ClosedShape.type, NotUsed] = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._

    val in: Source[Int, NotUsed] = Source(1 to 10)
    val out: Sink[Int, Future[Done]] = Sink.foreach[Int](x => println(x))

    val f1, f2, f3, f4 = Flow[Int].map(_ * 10) // Flow[Int, Int, NotUsed]

    val bcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))
    val merge: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))

    in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
    bcast ~> f4 ~> merge
    // construct the graph:
    // in -> f1 -> bcast -> f2 -> merge -> f3 -> out
    //                |---> f4 ---->|
    ClosedShape
  }
  val runnable1: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(graph1)
  val matValue1: NotUsed = runnable1.run() // run the graph: 1000, 1000, 2000, 2000, ..., 10000, 10000

  // 2)
  val topSink: Sink[Int, Future[Int]] = Sink.head[Int]
  val bottomSink: Sink[Int, Future[Int]] = Sink.head[Int]
  val flow2 = Flow[Int].map(_ * 2)

  val graph2: Graph[ClosedShape.type, (Future[Int], Future[Int])] = GraphDSL.create(topSink, bottomSink)((_, _)) { implicit builder: GraphDSL.Builder[(Future[Int], Future[Int])] =>
    (topSinkShape: SinkShape[Int], bottomSinkShape: SinkShape[Int]) =>
      import GraphDSL.Implicits._

      val in: Source[Int, NotUsed] = Source.single(1)
      val bcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))

      in ~> bcast ~> flow2 ~> topSinkShape.in
      bcast ~> flow2 ~> bottomSinkShape.in
      // construct the graph
      // in -> bcast -> flow2 -> topSink
      //         |----> flow2 -> bottomSink
      ClosedShape
  }
  val runnable2: RunnableGraph[(Future[Int], Future[Int])] = RunnableGraph.fromGraph(graph2)
  val matValue2: (Future[Int], Future[Int]) = runnable2.run() // run the graph:
  val future2 = Future.sequence(Seq(matValue2._1, matValue2._2))
  val result: Seq[Int] = Await.result(future2, 3.seconds)
  println(result) // List(2, 2)

  // 3) ???
  val flow3: Flow[Int, Int, NotUsed] = Flow[Int].map({ x => println(x); x})
  val graph3: Graph[ClosedShape.type, NotUsed] = GraphDSL.create(flow3) { implicit builder =>
    flowShape: Flow[Int, Int, NotUsed]#Shape  =>
    import GraphDSL.Implicits._

    val in: Source[Int, NotUsed] = Source.single(1)
    val merge: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))

    in ~> merge
    flowShape.out ~> merge ~> flowShape.in
    // construct the graph
    //  -- flowShape -->
    //  |<------------|
    ClosedShape
  }
  val runnable3: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(graph3)
  val matValue3: NotUsed = runnable3.run() // run the graph: no-op, as no element flow through the graph

  // 4) ???
  val graph4: Graph[SinkShape[Int], NotUsed] = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val in: FlowShape[Int, Int] = builder.add(Flow[Int])
    val merge: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))
    val print: FlowShape[Int, Int] = builder.add(Flow[Int].via(Flow[Int].map({ x => println(x); x})))
    in ~> merge ~> print ~> merge
    SinkShape(in.in)
  }
  val runnable4: RunnableGraph[NotUsed] = Source.single(1).to(graph4)
  val matValue4: NotUsed = runnable4.run() // run the graph: no-op, as no element flow through the graph

//  system.terminate()
}
