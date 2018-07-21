package akka_actors

import akka.actor.{Actor, ActorSystem, Props}

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

  class Counter extends Actor {
    var count = 0
    override def receive: Receive = {
      case "increment" => count += 1
      case "get" => sender ! count
    }
  }

  class Printer extends Actor {
    val counter = context.actorOf(Props[Counter], "counter") // create a counter Actor

    counter ! "increment"
    counter ! "increment"
    counter ! "increment"
    counter ! "get"

    override def receive: Receive = {
      case count: Int => {
        println(s"count=${count}")
        context.stop(self)
      }
    }
  }

  val system = ActorSystem("CounterExample")
  system.actorOf(Props[Printer], "printer") // count=3
  system.terminate()
}
