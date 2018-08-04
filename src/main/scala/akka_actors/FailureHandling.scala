package akka_actors

import akka.actor.SupervisorStrategy.{Escalate, Restart, Stop}
import akka.actor.{Actor, OneForOneStrategy, Props}

//1) where should failures go?
//   they are handled by passing messages, which are sent to some address
//2) failure supervision
//   failed Actor can be terminated or restarted, which is decided by its supervisor
//   the supervision hierarchy forms a tree, so that failing effect is of containment
object FailureHandling extends App {

  class Child extends Actor {
    override def receive: Receive = ???
  }

  object Supervisor {
    case class DBException() extends Exception
    case class ServiceDownException() extends Exception

  }
  class Supervisor extends Actor {
    import Supervisor._

    // the supervisor can decide what to do for its child failures
    // 1) OneForOneStrategy:
    //    this applies the fault handling to the child actor that failed, not to all children actors
    // 2) AllForOneStrategy:
    //    this applies the fault handling to all children actors (ex. for applications that children need to live or die together)
    override val supervisorStrategy = OneForOneStrategy() {
      // type Decider = PartialFunction[Throwable, Directive]
      case _: DBException => Restart       // restart its child actor in case of DBException
        // restart will reinstall the initial behavior/state of the failed child actor (i.e. reconstructed)
        //   ex. if the failed child actor has DB connection, the connection should be stopped & connected again
        // when the child actor is restarted, by default the child's child actors are restarted recursively (unless they are stopped explicitly)
      case _: ServiceDownException => Stop // stop its child actor in case of ServiceDownException
        // the child actor is can only be stopped once (i.e. terminated)
      case _: Exception => Escalate        // escalate the Exception to the supervisor's supervisor
    }

    context.actorOf(Props[Child], "child") // child actor of the supervisor actor

    override def receive: Receive = ???
  }


}
