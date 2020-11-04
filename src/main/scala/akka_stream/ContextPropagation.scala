package akka_stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, FlowWithContext, Sink, Source, SourceWithContext}

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
  implicit val system = ActorSystem("Substreams")
  implicit val materializer = ActorMaterializer()

  // 1) SourceWithContext.fromTuples(sourceOfTuples)
  //    creates a SourceWithContext from a source of tuple of elements: (data, context)
  val source1: Source[(String, Int), NotUsed] = Source(List(("element1" -> 1), ("element2" -> 2)))
  val sourceWithContext1: SourceWithContext[String, Int, NotUsed] = SourceWithContext.fromTuples(source1)
  sourceWithContext1.runWith(Sink.foreach(x => println(x))) // (element1,1), (element2,2)

  // 2) FlowWithContext.fromTuples(flowOfTuples)
  val flow2: Flow[(String, Int), (String, Int), NotUsed] = Flow[(String, Int)].map(x => (s"transformed ${x._1}", x._2))
  val flowWithContext2: FlowWithContext[String, Int, String, Int, NotUsed] = FlowWithContext.fromTuples(flow2)
  sourceWithContext1.via(flowWithContext2).runWith(Sink.foreach(x => println(x))) // (transformed element1,1), (transformed element2,2)

}
