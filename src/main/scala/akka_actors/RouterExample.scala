package akka_actors
import akka.actor.{Actor, ActorSystem, Props, Terminated}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}

object RouterExample extends App {

  class Worker extends Actor {
    def receive = {
      case _ => println("Worker receives message: Work")
    }
  }

  object Master {
    case object Work
  }
  class Master extends Actor {
    import Master._

    var router = {
      val routees = Vector.fill(5) {
        val routee = context.actorOf(Props[Worker])
        context watch routee
        ActorRefRoutee(routee)
      }
      Router(RoundRobinRoutingLogic(), routees)
    }

    def receive = {
      case Work =>
        router.route(Work, sender())
      case Terminated(routee) =>
        router = router.removeRoutee(routee)
        val newRoutee = context.actorOf(Props[Worker])
        context watch newRoutee
        router = router.addRoutee(newRoutee)
    }
  }

  val system = ActorSystem("RouterExample")
  val master = system.actorOf(Props[Master], "master")
  master ! Master.Work
  system.terminate()
}
