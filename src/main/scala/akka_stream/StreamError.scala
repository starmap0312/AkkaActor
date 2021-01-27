package akka_stream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{RestartFlow, RestartSource, Sink, Source}

import scala.concurrent.duration._
import scala.io.StdIn

// https://doc.akka.io/docs/akka/current/stream/stream-error.html
object StreamError extends App {
  implicit val system = ActorSystem("StreamError")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer() // an evaluation engine for the streams (note: akka streams are evaluated on top of actors)

  // 0) stream throws Exception w/o recover
  println("0) stream throws Exception w/o recover")
  Source(-5 to 5)
    .map({x => println(s"before: $x"); x})
    .map(x => 1 / x) // 1/-5 = 0, 1/-4 = 0, 1/-3 = 0, 1/-2 = 0, 1/-1 = -1, 1/0 --> throwing ArithmeticException: / by zero
    .runForeach(x => println(s"after: $x")) // 0, 0, 0, 0, -1, java.lang.ArithmeticException: / by zero

  Thread.sleep(3000)

  // 1) stream throws Exception w/ recover:
  //    it allows to send last element on failure and gracefully complete the stream
  //    i.e. it emits a final element then complete the stream normally on upstream failure
  println("1) stream throws Exception w/ recover")
  Source(-5 to 5)
    .map({x => println(s"before: $x"); x})
    .map(x => 1 / x) // 1/-5 = 0, 1/-4 = 0, 1/-3 = 0, 1/-2 = 0, 1/-1 = -1, 1/0 --> throwing ArithmeticException: / by zero
    .recover {
      case ex: ArithmeticException => s"${ex}" // java.lang.ArithmeticException: / by zero
    }
    .runForeach(x => println(s"after: $x")) // 0, 0, 0, 0, -1, java.lang.ArithmeticException: / by zero

  Thread.sleep(3000)

  // 2) stream throws Exception w/ recover recoverWithRetries([another source]):
  val planB = Source(List("five", "six", "seven", "eight"))
  println("2) stream throws Exception w/ recover recoverWithRetries([another source])")
  Source(0 to 10)
    .map({x => println(s"before: $x"); x})
    .map(n =>
      if (n < 5) n.toString
      else throw new RuntimeException("Boom!"))
    .recoverWithRetries(attempts = 1, {
      case _: RuntimeException => planB
    })
    .runForeach(x => println(s"after: $x")) // 0, 1, 2, 3, 4, five, six, seven, eight

  Thread.sleep(3000)

  // 3) stream throws Exception and enclosed by RestartSource.withBackoff():
  println("3) stream throws Exception and enclosed by RestartSource.withBackoff()")
  RestartSource.withBackoff(1.seconds, 60.seconds, 1)(() =>
    Source(-5 to 5)
      .map({x => println(s"before: $x"); x})
      .map(x => 1 / x) // 1/-5 = 0, 1/-4 = 0, 1/-3 = 0, 1/-2 = 0, 1/-1 = -1, 1/0 --> throwing ArithmeticException: / by zero
      .map(x => println(s"after: $x"))
  ).run()

  StdIn.readLine()
  system.terminate()
}