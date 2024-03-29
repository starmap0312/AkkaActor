package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.Attributes.LogLevels
import akka.stream._
import akka.stream.scaladsl.{BidiFlow, Broadcast, Flow, GraphDSL, Merge, Partition, RunnableGraph, Sink, Source}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

// https://doc.akka.io/docs/akka/current/stream/stream-graphs.html
// https://doc.akka.io/docs/akka/current/stream/operators/Partition.html
object Graphs extends App {
  implicit val system = ActorSystem("Graphs")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()

  // 1) GraphDSL.create() { buildBlock }:
  //    creates a new Graph by passing a Builder block to the given create function
  //    ex. ClosedShape with Broadcast & Merge:
  //        Broadcast: Fan-out the stream to several streams emitting each incoming upstream element to all downstream consumers
  //        Merge: Merge several streams, taking elements as they arrive from input streams,
  //               picking randomly when several have elements ready
  val source1: Source[Int, NotUsed] = Source(1 to 10)
  val sink1: Sink[Int, Future[Done]] = Sink.foreach[Int](x => println(x))

  val graph1: Graph[ClosedShape.type, NotUsed] = GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._
    val f1, f2, f3, f4 = Flow[Int].map(_ * 10) // Flow[Int, Int, NotUsed]

    val bcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))
    val merge: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))

    source1 ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> sink1
    bcast ~> f4 ~> merge
    // construct the graph:
    // source1 -> f1 -> bcast -> f2 -> merge -> f3 -> sink1
    //                    |---> f4 ---->|
    ClosedShape
  }
  val runnable1: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(graph1)
  val matValue1: NotUsed = runnable1.run() // run the graph: 1000, 1000, 2000, 2000, ..., 10000, 10000

  // 2) GraphDSL.create(graph1, graph2)(combineMat) { buildBlock }:
  //    Creates a new Graph by importing the given graphs and passing their Shapes along with the Builder block
  //    ex. ClosedShape: Broadcast & connect to two Sinks
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
  val matValue2: (Future[Int], Future[Int]) = runnable2.run() // run the graph
  val future2 = Future.sequence(Seq(matValue2._1, matValue2._2))
  val result: Seq[Int] = Await.result(future2, 3.seconds)
  println(result) // List(2, 2)


  // 3) Combining Sources and Sinks with simplified API
  //   a simplified API you can use to combine sources and sinks with junctions, ex. Broadcast[T], Balance[T], Merge[In] and Concat[A]
  //   without the need for using the Graph DSL
  val sourceOne: Source[Int, NotUsed] = Source.single(1)
  val sourceTwo: Source[Int, NotUsed] = Source.single(2)
  val sourceMerged: Source[Int, NotUsed] = Source.combine(sourceOne, sourceTwo)(Merge(_))

  val mergedResult: Future[Done] = sourceMerged.runWith(Sink.foreach[Int](println(_))) // 1, 2
  Await.result(mergedResult, 1.second)

  // 3.2) Merge two sources. Prefer one source if both sources have elements ready.
  //      if multiple have elements available, prefer the 'right' one when 'preferred' is 'true'
  val sourceMerged2 = sourceOne.mergePreferred(sourceTwo, priority = true)
  val mergedResult2: Future[Done] = sourceMerged2.runWith(Sink.foreach[Int](println(_))) // 2, 1
  Await.result(mergedResult2, 1.second)

  // Fan-out operators
  // 4) Partition:	Fan-out the stream to several streams
  //    Each upstream element is emitted to one downstream consumer according to the partitioner function applied to the element
  println("Partition")
  val source4: Source[Int, NotUsed] = Source(1 to 5)
  val even4: Sink[Int, NotUsed] =
    Flow[Int]
    .log("even").withAttributes(Attributes.logLevels(onElement = LogLevels.Info))
    .to(Sink.ignore)
  val odd4: Sink[Int, NotUsed] =
    Flow[Int]
      .log("odd")
      .withAttributes(Attributes.logLevels(onElement = LogLevels.Info))
      .to(Sink.ignore)

  val graph4  = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val partition = builder.add(Partition[Int](2, element => if (element % 2 == 0) 0 else 1))
    source4 ~> partition.in
    partition.out(0) ~> even4
    partition.out(1) ~> odd4
    ClosedShape
  }
  val runnable4: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(graph4)
  val matValue4: NotUsed = runnable4.run()
  // [INFO] [11/02/2020 13:47:48.762] [odd] Element: 1
  // [INFO] [11/02/2020 13:47:48.763] [even] Element: 2
  // [INFO] [11/02/2020 13:47:48.763] [odd] Element: 3
  // [INFO] [11/02/2020 13:47:48.763] [even] Element: 4
  // [INFO] [11/02/2020 13:47:48.763] [odd] Element: 5

  // 5) Partition & Merge
  Thread.sleep(3000)
  println("Partition & Merge")
  val source5: Source[Int, NotUsed] = Source(1 to 5)
  val even5: Flow[Int, Int, NotUsed] = Flow[Int]
      .log("even").withAttributes(Attributes.logLevels(onElement = LogLevels.Info))
      .map(x => x)
  val odd5: Flow[Int, Int, NotUsed] = Flow[Int]
      .log("odd").withAttributes(Attributes.logLevels(onElement = LogLevels.Info))
      .map(x => x)
  val flow5: Flow[Int, Int, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val partition = builder.add(Partition[Int](2, element => if (element % 2 == 0) 0 else 1))
    val merge = builder.add(Merge[Int](2))
    partition.out(0) ~> even5 ~> merge
    partition.out(1) ~> odd5 ~> merge
    FlowShape(partition.in, merge.out)
  })
  val runnable5 = source5.via(flow5).to(Sink.ignore).run
  // [INFO] [11/02/2020 13:47:48.762] [odd] Element: 1
  // [INFO] [11/02/2020 13:47:48.763] [even] Element: 2
  // [INFO] [11/02/2020 13:47:48.763] [odd] Element: 3
  // [INFO] [11/02/2020 13:47:48.763] [even] Element: 4
  // [INFO] [11/02/2020 13:47:48.763] [odd] Element: 5

  // 6) Bidirectional Flows
  println("Bidirectional Flows")
  def encode(x: Int) = {
    println(s"x + 1 = ${x + 1}")
    x + 1
  }
  def decode(x: Int) = {
    println(s"x - 1 = ${x - 1}")
    x - 1
  }
  def identity(x: Int) = {
    println(s"x = ${x}")
    x
  }
  val source = Source(0 to 2)
  val codec: BidiFlow[Int, Int, Int, Int, NotUsed] = BidiFlow.fromFunctions(encode, decode)
  val flow: Flow[Int, Int, NotUsed] = codec.join(Flow.fromFunction(identity))
  val sink = Sink.foreach(println)
  // source -> encode -
  //                   |
  //                identity
  //                   |
  // sink   <- decode -
  source.via(flow).to(sink).run
  // x + 1 = 1
  // x = 1
  // x - 1 = 0
  // 0
  // x + 1 = 2
  // x = 2
  // x - 1 = 1
  // 1
  // x + 1 = 3
  // x = 3
  // x - 1 = 2
  // 2
  system.terminate()
}
