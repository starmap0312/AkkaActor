package akka_stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Source}

import scala.concurrent.Future

object StreamOperators extends App {
  implicit val system = ActorSystem("StreamOperators")
//  implicit val dispatcher = system.dispatcher
//  implicit val materializer = ActorMaterializer()

  // 1) Source.unfold[S, E](s: S)(f: S => Option[(S, E)])
  //    streams the result of a function as long as it returns a Some
  println("Source.unfold: countDown")
  val countDown: Source[Int, NotUsed] = Source.unfold(5) { current =>
    if (current == 0) None
    else Some((current - 1, current * 2))
  }
  countDown.runForeach(println) // (5 * 2), (4 * 2), (3 * 2), (2 * 2), (1 * 2)

  Thread.sleep(1000)

  println("Source.unfold: fibonacci")
  val fibonacci: Source[Int, NotUsed] = Source.unfold(0 -> 1) {
    case (a, b) => Some(((b, a + b), a))
  }.take(5)
  fibonacci.runForeach(println) // 0, 1, 1, 2, 3

  Thread.sleep(1000)

  // 2) Flow.lazyFlow
  //     this defers creation and materialization of a Flow until there is a first element
  println("Flow.lazyFlow")
  val numbers: Source[Int, NotUsed] = Source.unfold(0) { n =>
    // unfold: Create a Source that will unfold a value of type Int into a pair of the next state Int and output elements of type Int
    val next = n + 1
    println(s"Source producing $next") // Source producing 1, 2, 3
    Some((next, next))
  }.take(3)
  val lazyFlow: Flow[Int, Int, Future[NotUsed]] = Flow.lazyFlow { () =>
    println("Creating the actual flow")
    Flow[Int].map { element =>
      println(s"Actual flow mapped $element")
      element
    }
  }
  numbers.via(lazyFlow).run()
  // Source producing 1
  // Creating the actual flow -> note: producing the first value in the Source happens before the creation of the flow
  // Actual flow mapped 1
  // Source producing 2
  // Actual flow mapped 2
  // Source producing 3
  // Actual flow mapped 3

  Thread.sleep(1000)
  println("Flow.lazyFlow")
  val normalFlow: Flow[Int, Int, NotUsed] = {
    println("Creating the actual flow")
    Flow[Int].map { element =>
      println(s"Actual flow mapped $element")
      element
    }
  }
  numbers.via(normalFlow).run()
  // Creating the actual flow -> note: the creation of the flow happens before producing the first value in the Source
  // Source producing 1
  // Actual flow mapped 1
  // Source producing 2
  // Actual flow mapped 2
  // Source producing 3
  // Actual flow mapped 3

  Thread.sleep(1000)
  system.terminate()
}
