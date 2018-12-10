package akka_futures

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.io.StdIn
import scala.util.{Random, Success}

// a good guideline explaining mapAsync & async boundary:
// - https://blog.colinbreck.com/maximizing-throughput-for-akka-streams/
// - https://doc.akka.io/docs/akka/current/stream/stream-flows-and-basics.html?language=scala#operator-fusion
// - https://doc.akka.io/docs/akka/2.5.5/scala/stream/stream-integrations.html

object AsyncMapExample extends App {
  implicit val system = ActorSystem("AsyncMapExample")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer() // an evaluation engine for the streams (note: akka streams are evaluated on top of actors)

  // Asynchronous Boundaries:
  //  i) transparent to the programmer
  // ii) an Akka stream is executed by a single actor, leveraging operator fusion
  //     Operator Fusion:
  //     by default, Akka Streams fuses the stream operators: i.e. the processing steps of a flow or stream is executed within the same Actor
  //     this avoids the asynchronous messaging overhead and only one CPU core is used for each fused part
  //iii) use .async method to insert asynchronous boundaries manually into the flow

  // Simulate a CPU-intensive workload that takes ~10 milliseconds
  def spin(value: Int): Int = {
    val start = System.currentTimeMillis()
    while ((System.currentTimeMillis() - start) < 10) {}
    value
  }

  // 0) default: single Asynchronous Boundary
  Source(1 to 100)
    .map(spin)
    .map(spin)
    .runWith(Sink.ignore)
    .onComplete {
      case Success(Done) => println("Done: ex0.") // prints Done when the stream is complete
    }

  // 1) .async(): insert async method means that each map stage will be executed in a separate actor
  //   i.e. asynchronous message-passing is used to communicate between the actors, across the asynchronous boundary
  //   this helps with Maximizing Throughput for Akka Streams but with overhead of asynchronous messaging
  Source(1 to 100)
    .map(spin)
    .async
    .map(spin)
    .runWith(Sink.ignore)
    .onComplete {
      case Success(Done) => println("Done: ex1.") // prints Done when the stream is complete
    }
  // when an asynchronous boundary is introduced, the Akka Streams API inserts a buffer between every asynchronous processing stage
  //   default buffer size = 16 elements
  //   this supports a windowed backpressure-strategy, where new elements are requested in batches,
  //   this amortizes the cost of requesting elements across the asynchronous boundary between flow stages

  // 2) .mapAsync([parallelism])([fn]): adjusting the overall .mapAsync() parallelism to saturate the available cores
  //   when .async() does not introduce sufficient parallelism, use .mapAsync([parallelism]) instead of map([fn]) to capture the work in a Future
  //   this increases the parallelism and saturates the available cores
  Source(1 to 100)
    .mapAsync(4)(num => Future(spin(num)))
    .mapAsync(4)(num => Future(spin(num)))
    .runWith(Sink.ignore)
    .onComplete {
      case Success(Done) => println("Done: ex2.") // prints Done when the stream is complete
    }
  // the mapAsync() flow stage introduces asynchrony:
  // the Future will be executed on a thread of the execution context, rather than by the actor executing the flow stage
  // note: it does not introduce an asynchronous boundary into the flow

  // 2.1) Simulate a non-uniform CPU-bound workload
  val random = new Random()
  def randomSpin(value: Int): Future[Int] = Future {
    // the duration of the workload is selected at random, uniformly distributed between 0 milliseconds and 100 milliseconds
    val max = random.nextInt(101)
    val start = System.currentTimeMillis()
    while ((System.currentTimeMillis() - start) < max) {}
    value
  }

  // the following stream will be fused and executed by a single actor
  Source(1 to 100)
    .mapAsync(1)(randomSpin)
    .mapAsync(1)(randomSpin)
    .mapAsync(1)(randomSpin)
    .mapAsync(1)(randomSpin)
    .runWith(Sink.ignore)
    .onComplete {
      case Success(Done) => println("Done: ex2.1.") // prints Done when the stream is complete
    }
  // 2.2) the stream will execute more efficiently if an asynchronous boundary is inserted between each mapAsync element
  //      this introduces buffer to in-between steps and further decouples the stages (default buffer size = 16 elements)
  Source(1 to 100)
    .mapAsync(1)(randomSpin).async
    .mapAsync(1)(randomSpin).async
    .mapAsync(1)(randomSpin).async
    .mapAsync(1)(randomSpin).async
    .runWith(Sink.ignore)
    .onComplete {
      case Success(Done) => println("Done: ex2.2.") // prints Done when the stream is complete
    }

  // 2.3) it would be even more efficient to just compose the stream as follows
  Source(1 to 100)
    .mapAsync(4)(randomSpin)
    .runWith(Sink.ignore)
    .onComplete {
      case Success(Done) => println("Done: ex2.3.") // prints Done when the stream is complete
    }


  // 3) non-CUP bound workload: ex. a non-blocking network request, or I/O access
  //    throughput can be improved by increasing the .mapAsync() parallelism far beyond the number of cores: ex. 1000

  // Simulate a non-blocking network call to another service
  def networkCall(value: Int): Future[Int] = {
    val promise = Promise[Int]
    val max = FiniteDuration(random.nextInt(101), MILLISECONDS) // the Future will be completed in a random duration, uniformly distributed between 0 milliseconds and 100 milliseconds
    system.scheduler.scheduleOnce(max) {
      promise.success(value)
    }
    promise.future
  }
  Source(1 to 100)
    .mapAsync(1000)(networkCall) // as the work is I/O bound, we can set the parallelism to be much larger than # of cup cores
    .runWith(Sink.ignore)
    .onComplete {
      case Success(Done) => println("Done: ex3.") // prints Done when the stream is complete
    }

  StdIn.readLine()
  system.terminate()
}
