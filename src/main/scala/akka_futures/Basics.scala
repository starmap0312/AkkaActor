package akka_futures

import akka.actor.ActorSystem
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// https://doc.akka.io/docs/akka/2.5/futures.html
object Basics extends App {
  val system = ActorSystem("FutureExample")
  import system.dispatcher

  val longTask = Future {
    Thread.sleep(3000)
    1000
  }
  val scheduledTask = akka.pattern.after(1000.millis, using = system.scheduler)(
    Future.failed[Int](new RuntimeException("schedule a future exception"))
  )
  // Future.firstCompletedOf(Seq[Future]):
  //   returns a new Future to the result of the first future in the list that is completed
  val firstCompletedTask = Future.firstCompletedOf(Seq(longTask, scheduledTask))
  firstCompletedTask onComplete  {
    case Success(result) => println(result)
    case Failure(ex) => println(ex.getMessage) // schedule a future exception
  }
  system.terminate()
}
