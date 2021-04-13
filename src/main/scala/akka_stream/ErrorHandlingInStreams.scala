package akka_stream

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{RestartSource, Source}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}

// Error Handling in Streams: https://doc.akka.io/docs/akka/current/stream/stream-error.html
object ErrorHandlingInStreams extends App {
  implicit val system = ActorSystem("StreamError")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer() // an evaluation engine for the streams (note: akka streams are evaluated on top of actors)

  // 0) stream throws Exception w/o recover
  println("0) stream throws Exception handled by onComplete")
  val future0: Future[Done] = Source(-5 to 5)
    .map({x => println(s"before: $x"); x})
    .map { i =>
      if (i == 2) throw new RuntimeException("Please, don't swallow me!")
      else i
    }.runForeach(x => println(s"after: $x"))
  future0.onComplete { // as runForeach returns Future[Done], we could check if the stream is successful or not
    case Success(_) => println("Done")
    case Failure(ex) => println(s"Failed with $ex") // Failed with java.lang.RuntimeException: Please, don't swallow me!
  }
  Thread.sleep(3000)
  println(future0) // future completes w/ failure: Future(Failure(java.lang.RuntimeException: Please, don't swallow me!))

  // 1) stream throws Exception w/o recover
  println("1) stream throws Exception w/o recover")
  val future1: Future[Done] = Source(-5 to 5)
    .map({x => println(s"before: $x"); x})
    .map(x => 1 / x) // 1/-5 = 0, 1/-4 = 0, 1/-3 = 0, 1/-2 = 0, 1/-1 = -1, 1/0 --> throwing ArithmeticException: / by zero
    .runForeach(x => println(s"after: $x")) // 0, 0, 0, 0, -1, java.lang.ArithmeticException: / by zero
  Thread.sleep(3000)
  println(future1) // Future(Failure(java.lang.ArithmeticException: / by zero))

  // 2) stream throws Exception w/ recover:
  //    it allows to send last element on failure and gracefully complete the stream
  //    i.e. it emits a final element then complete the stream normally on upstream failure
  println("2) stream throws Exception w/ recover")
  val future2: Future[Done] = Source(-5 to 5)
    .map({x => println(s"before: $x"); x})
    .map(x => 1 / x) // 1/-5 = 0, 1/-4 = 0, 1/-3 = 0, 1/-2 = 0, 1/-1 = -1, 1/0 --> throwing ArithmeticException: / by zero
    .recover {
      case ex: ArithmeticException => s"${ex}" // java.lang.ArithmeticException: / by zero
    }
    .runForeach(x => println(s"after: $x")) // 0, 0, 0, 0, -1, java.lang.ArithmeticException: / by zero
  Thread.sleep(3000)
  println(future2) // future completes successfully: Future(Success(Done))

  // 3) stream throws Exception w/ recover recoverWithRetries([another source]):
  val planB = Source(List("five", "six", "seven", "eight"))
  println("3) stream throws Exception w/ recover recoverWithRetries([another source])")
  val future3: Future[Done] = Source(0 to 10)
    .map({x => println(s"before: $x"); x})
    .map(n =>
      if (n < 5) n.toString
      else throw new RuntimeException("Boom!"))
    .recoverWithRetries(attempts = 1, {
      case _: RuntimeException => planB
    })
    .runForeach(x => println(s"after: $x")) // 0, 1, 2, 3, 4, five, six, seven, eight
  Thread.sleep(3000)
  println(future3) // future completes successfully: Future(Success(Done))

  // 4) stream throws Exception and enclosed by RestartSource.withBackoff():
  println("4) stream throws Exception and enclosed by RestartSource.withBackoff()")
  val future4: Future[Done] = RestartSource.withBackoff(1.seconds, 10.seconds, 0.2, 3)(() => // this will restart the graph for failures
    Source(-5 to 5)
      .map({x => println(s"before: $x"); x})
      .map(x => 1 / x) // 1/-5 = 0, 1/-4 = 0, 1/-3 = 0, 1/-2 = 0, 1/-1 = -1, 1/0 --> throwing ArithmeticException: / by zero
      .map(x => println(s"after: $x"))
  ).run() // returns Future[Done]
  Thread.sleep(3000)
  println(future4) // future never completes as the Source is restarted infinitely: Future(<not completed>)

  // i)  note that this Source will not emit a complete or failure as long as maxRestarts is not reached
  //     since the completion or failure of the wrapped Source is handled by restarting it
  // ii) however, the wrapped Source can however be cancelled by "cancelling" this Source
  //     when that happens, the wrapped Source, if currently running will be cancelled, and it will not be restarted.
  //     this can be triggered simply by the downstream cancelling, or externally by introducing a KillSwitch right after this Source in the graph.
  StdIn.readLine()
  system.terminate()
}
