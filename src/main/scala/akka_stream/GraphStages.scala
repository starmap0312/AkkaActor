package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

// https://doc.akka.io/docs/akka/current/stream/stream-customize.html
// Custom stream processing:
// 1) create a custom operator, ex. map, filter, etc.
// 2) GraphDSL.create() vs. GraphStage:
//    GraphDSL.create(): creates new stream processing operators by composing others
//    GraphStage: creates an operator that is itself not divisible into smaller ones
//                this allows state to be maintained inside it in a safe way

// https://doc.akka.io/docs/akka/current/stream/stream-customize.html#custom-linear-operators-using-graphstage

object GraphStages extends App {
  implicit val system = ActorSystem("GraphStages")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()

  // 1) build a new custom Source that will emit numbers from 1 until it is cancelled
  // first, define the "interface" of our operator, i.e. shape
  class NumbersSource extends GraphStage[SourceShape[Int]] {
    // Define the (sole) output port of this stage
    val out: Outlet[Int] = Outlet("NumbersSource") // the ports of this operator

    // Define the shape of this stage, which is SourceShape with the port we defined above
    override val shape: SourceShape[Int] = SourceShape(out) // a shape that contains the ports

    // This is where the actual (possibly stateful) logic will live
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
      new GraphStageLogic(shape) {
        // All state MUST be inside the GraphStageLogic, never inside the enclosing GraphStage.
        // This state is safe to access and modify from all the callbacks that are provided by GraphStageLogic and the registered handlers.
        private var counter = 1

        // Output port handler
        setHandler(out, new OutHandler { // This handler has two callbacks: onPull() & onDownstreamFinish()
          override def onPull(): Unit = {
            // onPull():
            //   this is called when the output port is ready to emit the next element
            //   push(out, elem) is now allowed to be called on this port.

            push(out, counter)
            // push(out, elem):
            //   this pushes an element to the output port, i.e. out Outlet
            //   this is only possible after the port has been pulled by downstream.

            counter += 1
          }
        })
      }
    }
  }

  // use of the NumbersSource
  // A GraphStage is a proper Graph, just like what GraphDSL.create would return
  val sourceGraph1: Graph[SourceShape[Int], NotUsed] = new NumbersSource
  // Create a Source from the Graph to access the DSL
  val source1: Source[Int, NotUsed] = Source.fromGraph(sourceGraph1)
  val future1: Future[Int] = source1.take(10).runFold(0)(_ + _) // 1 + 2 + ... + 10 = 55
  println("test 1")
  println(Await.result(future1, 3.seconds)) // print 55


  // 2) build a new custom Sink that will print out the number to stdout
  class StdoutSink extends GraphStage[SinkShape[Int]] {

    val in: Inlet[Int] = Inlet("StdoutSink") // the ports of this operator
    override val shape: SinkShape[Int] = SinkShape(in)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
      new GraphStageLogic(shape) {

        // note: this requests one element at the Sink startup! (that's why the stream starts to flaw once run?)
        override def preStart(): Unit = pull(in)

        setHandler(in, new InHandler { // This handler has three callbacks: onPush() & onUpstreamFinish() & onUpstreamFailure
          override def onPush(): Unit = {
            // onPush():
            //  it is not mandatory to grab the element, but
            //  if it is pulled while the element has not been grabbed it will drop the buffered element (discarded)

            val element = grab(in)
            // acquire this element that has been received during an onPush()
            // it cannot be called again until the port is pushed again by the upstream

            println(element)

            pull(in)
            // request a new (next) element from the input port
            // this is only possible after the port has been pushed by upstream
          }
        })
      }
    }
  }

  val sinkGraph2: Graph[SinkShape[Int], NotUsed] = new StdoutSink
  // Create a Source from the Graph to access the DSL
  val sink2: Sink[Int, NotUsed] = Sink.fromGraph(sinkGraph2)
  println("test 2")
  val matValue2: NotUsed = source1.take(5).runWith(sink2) // print 1, 2, 3, 4, 5

  // 3) build a new custom operator that will apply a function to the numbers
  class Map[A, B](f: A => B) extends GraphStage[FlowShape[A, B]] {

    val in = Inlet[A]("Map.in")
    val out = Outlet[B]("Map.out")

    override val shape = FlowShape.of(in, out)

    override def createLogic(attr: Attributes): GraphStageLogic = {
      new GraphStageLogic(shape) {
        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            val element = grab(in)
            // it cannot be called again until the port is pushed again by the upstream
            // ex. IllegalArgumentException: Cannot get element from already empty input port

            push(out, f(element))
            // this is only possible after the port has been pulled by downstream
          }
        })
        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
            // this is only possible after the port has been pushed by upstream
          }
        })
      }
    }
  }
  val mapGraph3: Graph[FlowShape[Int, Int], NotUsed] = new Map(_ * 2)
  val mapper3: Flow[Int, Int, NotUsed] = Flow.fromGraph(mapGraph3)
  println("test 3")
  val result3: NotUsed = source1.take(3).via(mapper3).runWith(sink2) // print 2, 4, 6

  system.terminate()
}
