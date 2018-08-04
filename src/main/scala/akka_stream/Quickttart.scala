package akka_stream

import java.nio.file.Paths

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future

object Quickttart extends App {
  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()

  // 1) Source:       a Source is a description of the source you want to run, which can be transformed
  // 2) Sink:         a Sink is a set of stream processing steps that has one open input. it can be used as a Subscriber
  // 3) Materializer: a Materializer is a factory for stream execution engines, it is the thing that makes streams run
  //                    i.e. you need it for calling any of the run methods on a Source
  // 4) Flow:         a Flow is a processing stage which has exactly one input and output
  //                  it connects its up- and downstreams by transforming the data elements flowing through it

  // example1: a simple source, emitting the integers 1 to 100
  val source: Source[Int, NotUsed] = Source(1 to 10)
  // note:
  // the second type: NotUsed signals that running the source produces some auxiliary value
  //   e.g. a network source may provide information about the bound port or the peer’s address
  // where no auxiliary information is produced, the type akka.NotUsed


  // 1) source.runForeach([func]) = runWith(Sink.foreach([func]))
  //    running this Source with a foreach procedure
  // 2) runWith():
  //    connecting this Source to a Sink and run it
  val done: Future[Done] = source.runForeach(num => println(num))(materializer)

  // example2: source.scan([initial value])([func]): like foldLeft()?
  //   use the scan operator to run a computation over the whole stream: starting with the number 1
  //   i.e. 1, 1 * 1, 1 * 1 * 2, 1 * 1 * 2 * 3 ...
  //        1 1 2 6 24 120 ...
  //   note: nothing is actually computed yet, this is a description of what we want to do once we run the stream
  val factorials = source.scan(BigInt(1))((acc, next) => acc * next)
  // def scan(zero: BigInt)(func: (BigInt, Out) => BigInt): Repr[BigInt]
  //   returns: type Repr[BigInt] = Flow[In, BigInt, Mat]


  // 3) FileIO.toPath([Path]):
  //      returns: Sink[ByteString, Future[IOResult]], note: Sink[-In, +Mat] where In: ByteString, Mat: Future[IOResult]]
  val result: Future[IOResult] = // IOResult is what IO operations returns to tell you how many elements were consumed and whether the stream terminated normally
    factorials
      .map(num => ByteString(s"$num\n")) // transform the resulting series of numbers into a stream of ByteString objects
      .runWith(FileIO.toPath(Paths.get("factorials.txt"))) // the stream is then run by attaching a file (Sink) as the receiver of the data

  // example3:
  val tweets: Source[String, NotUsed] = Source("tweet1" :: "tweet2" :: Nil)
  tweets
    .map(_.toUpperCase) // Get all sets of tweets ...
    .reduce(_ ++ ", " ++ _) // reduce them to a single set
    .runWith(Sink.foreach(println)) // Attach the Flow to a Sink that will finally print the tweets
}
