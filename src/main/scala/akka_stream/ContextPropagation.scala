package akka_stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, FlowWithContext, Sink, Source, SourceWithContext}
import akka_stream.ContextPropagation.sourceWithContext1

// https://doc.akka.io/docs/akka/current/stream/stream-context.html
// Context Propagation:
//   used to attach metadata to each element in the stream
//   ex. when reading from an external data source it can be useful to keep track of the read offset
//       when the element reaches the Sink, we can then use the context (attached metadata), to mark the offset being processed in a persistent system, ex. log to a file
//       this helps us to recover/resume the process
// Restrictions
//   Not all Flow operations that are available on FlowWithContext
//   ex. in keeping track of a read offset, if we reorder the stream,
//       the Sink would have no way to determine whether an element was filtered, reordered, or still in flight
//       therefore, we disallow reorder the stream when using FlowWithContext
//   Allow: filtering operations: ex. filter(), filterNot(), collect(), etc.
//     drop the context along with the element
//   Allow: grouping operations: ex. grouped(), sliding(), etc.
//     keep all contexts from the elements in the group
//   Allow: one-to-n operations: ex. mapConcat(), etc.
//     associate the original context with each of the produced elements
//   Disallow: reordering operations: ex. mapAsyncUnordered(), statefulMapConcat(), etc.
object ContextPropagation extends App {
  implicit val system = ActorSystem("ContextPropagation")
  implicit val materializer = ActorMaterializer()

  // 1) SourceWithContext.fromTuples(sourceOfTuples)
  //    creates a SourceWithContext from a source of tuple of elements: (data, context)
  val source1: Source[(String, Int), NotUsed] = Source(List(("element1" -> 1), ("element2" -> 2)))
  val sourceWithContext1: SourceWithContext[String, Int, NotUsed] = SourceWithContext.fromTuples(source1)
  sourceWithContext1.runWith(Sink.foreach(x => println(x))) // (element1,1), (element2,2)

  Thread.sleep(1000)
  // SourceWithContext.asSource
  val source12: Source[(String, Int), NotUsed] = sourceWithContext1.asSource
  source12.runWith(Sink.foreach(x => println(x))) // (element1,1), (element2,2)

  Thread.sleep(1000)
  // 2) FlowWithContext.fromTuples(flowOfTuples)
  val flow2: Flow[(String, Int), (String, Int), NotUsed] = Flow[(String, Int)].map(x => (s"transformed ${x._1}", x._2))
  val flowWithContext2: FlowWithContext[String, Int, String, Int, NotUsed] = FlowWithContext.fromTuples(flow2)
  sourceWithContext1.via(flowWithContext2).runWith(Sink.foreach(x => println(x))) // (transformed element1,1), (transformed element2,2)

  Thread.sleep(1000)

  // FlowWithContext.asFlow
  val flow12: Flow[(String, Int), (String, Int), NotUsed] = flowWithContext2.asFlow
  source12.via(flow12).runWith(Sink.foreach(x => println(x))) // ((transformed element1,1), (transformed element2,2)

  Thread.sleep(1000)

  // 3) Flow of Tuples Flow[(String, Int), (String, Int), NotUsed] & FlowWithContext[String, Int, String, Int, NotUsed] can be used interchangeably
  val flowWithContext3: FlowWithContext[String, Int, String, Int, NotUsed]#Repr[String, Int] = FlowWithContext[String, Int].map(str => s"${str} in flow3" )
  val flow4 = Flow[(String, Int)].map(x => s"${x._1} in flow4" -> x._2)
  val source3: SourceWithContext[String, Int, NotUsed] = sourceWithContext1.via(flowWithContext2).via(flowWithContext3).via(flow4)
  source3.runWith(Sink.foreach(x => println(x))) // (transformed element1 in flow3 in flow4,1), (transformed element2 in flow3 in flow4,2)
  Thread.sleep(1000)
  val source32: Source[(String, Int), NotUsed]  = source1.via(flowWithContext2).via(flowWithContext3).via(flow4)
  source32.runWith(Sink.foreach(x => println(x))) // (transformed element1 in flow3 in flow4,1), (transformed element2 in flow3 in flow4,2)

//  val flow3: Flow[(String, Int), (String, Int), NotUsed] = FlowWithContext[String, Int].map(str => s"${str} in flow3" ).asFlow


}
