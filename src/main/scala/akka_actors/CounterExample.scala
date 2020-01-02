package akka_actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}

import scala.io.StdIn

// 1) Actor class:
//
//    trait Actor {
//      implicit val self: ActorRef ... returns its reference
//      def sender:        ActorRef ... returns its sender's reference
//
//      implicit val context: ActorContext ... with become() & unbecome() method to change the Actor's receive behavior
//      ex. context.become([Receive])
//
//      def receive: Receive        ... this method must be implemented (override)
//    }
//    note: type Receive = PartialFunction[Any, Unit]
//          i.e. this defines what to do (so it returns type Unit) when the Actor receives a message (of type Any)
// 2) ActorRef class:
//    abstract class ActorRef {
//      def !(msg: Any)(implicit sender: ActorRef = Actor.noSender): Unit ... tell the actor reference the msg
//      def tell(msg: Any, sender: ActorRef) = this.!(msg)(sender)
//    }
// 3) ActorContext class:
//    trait ActorContext {
//      def become(behavior: Receive, discardOld: Boolean = true): Unit
//      def unbecome(): Unit
//    }
object CounterExample extends App {

  class Counter extends Actor with ActorLogging {
    var count = 0
    override def receive: Receive = {
      case "increment" => {
        log.info("actor Counter recevies increment") // INFO akka_actors.CounterExample$Counter - actor Counter recevies increment
        count += 1
      }
      case "get" => {
        log.debug("debug message") // INFO akka_actors.CounterExample$Counter - actor Counter recevies get
        log.info("actor Counter recevies get") // INFO akka_actors.CounterExample$Counter - actor Counter recevies get
        sender ! count
      }
    }
  }

  class Printer extends Actor with ActorLogging {
    val counter = context.actorOf(Props[Counter], "counter") // create a counter Actor

    counter ! "increment"
    counter ! "increment"
    counter ! "increment"
    counter ! "get"

    override def receive: Receive = {
      case count: Int => {
        log.info("count={}", count) // [DEBUG] [07/22/2018 15:42:38.645] [akka://CounterExample/user/printer] count=3
        context.stop(self)
      }
    }
  }

  val system = ActorSystem("CounterExample")
  val actorRef = system.actorOf(Props[Printer], "printer") // count=3
  StdIn.readLine()
  system.terminate()
}
