package akka_actors

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TimeoutExample extends App {

  class Worker extends Actor {
    override def receive: Receive = {
      case msg =>
        println(s"Received $msg")
        Thread.sleep(3000)
        sender() ! "completed"
        context.stop(self)
    }
  }
  val system = ActorSystem("TimeoutExample")
  import system.dispatcher

  val actorRef = system.actorOf(Props[Worker], "worker")

  // actorRef.ask([msg])(implicit Timeout, sender = Actor.noSender): set a timeout to ask
  implicit val timeout = Timeout(1.seconds) // the implicit Timeout is required for ask another actor
  val resultFuture = actorRef ? "hi" // returns a Future which may be a Failure(AskTimeoutException)

  resultFuture onComplete {
    case Success(result) => println(result)
    case Failure(ex) => println(ex.getMessage) // AskTimeoutException: the recipient actor didn't send a reply within timeout
  }

  system.terminate()

}
