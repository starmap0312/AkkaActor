package akka_futures

import akka.actor.ActorSystem

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success, Try}

// https://doc.akka.io/docs/akka/2.5/futures.html
// https://nami.me/2015/01/20/scala-futures-with-timeout/
object FutureTimeout extends App {
  val system = ActorSystem("FutureExample")
  import system.dispatcher

  // 1) blocking future timeout:
  //    Await.result([future], [timeout]): throws TimeoutException if exceeds [timeout] seconds
  //    note: blocking on a future is not recommended because it wastes a thread
  val longTask1 = Future {
    Thread.sleep(3000)
    1000
  }
  Try(Await.result(longTask1, 1.seconds)) match {
    case Success(result) => println(result)
    case Failure(ex) => println(ex.getMessage) // Futures timed out after [1 second]
  }

  // 2) non-blocking future timeout:
  //    Future.firstCompletedOf([future], [timeout future by scheduler]):
  lazy val longTask2 = Future {
    Thread.sleep(3000)
    1000
  }
  // note: lazy is required so that it meets our timeout logic: i.e. the future should not have started in advance

  // akka.pattern.after([timeout], [scheduler]):
  //   a future that will be completed with the success or failure of the provided value after the specified duration
  lazy val timeoutTask = akka.pattern.after(1000.millis, using = system.scheduler)(
    Future.failed[Int](new RuntimeException("Futures timed out after [1 second]"))
  )
  // note: lazy is required so that it meets our timeout logic: i.e. the future should not have started in advance

  // Future.firstCompletedOf(Seq[Future]):
  //   returns a new Future to the result of the first future in the list that is completed
  val firstCompletedTask = Future.firstCompletedOf(Seq(longTask2, timeoutTask))
  firstCompletedTask onComplete  {
    case Success(result) => println(result)
    case Failure(ex) => println(ex.getMessage) // schedule a future exception
  }
  //StdIn.readLine()
  system.terminate()
}
