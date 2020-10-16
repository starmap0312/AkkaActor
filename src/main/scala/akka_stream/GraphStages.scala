package akka_stream

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}

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

  // 1) build a new Source that will emit numbers from 1 until it is cancelled
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

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            push(out, counter)
            counter += 1
          }
        })
      }
    }
  }

  // use of the NumbersSource
  // A GraphStage is a proper Graph, just like what GraphDSL.create would return
  val sourceGraph: Graph[SourceShape[Int], NotUsed] = new NumbersSource
  // Create a Source from the Graph to access the DSL
  val source1: Source[Int, NotUsed] = Source.fromGraph(sourceGraph)
  val future1: Future[Int] = source1.take(10).runFold(0)(_ + _) // 1 + 2 + ... + 10 = 55
  println(Await.result(future1, 3.seconds))

  system.terminate()
}
