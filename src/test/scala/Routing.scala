import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.routing._
import org.scalatest._
import akka.testkit.{TestKit, TestProbe}

// 1) A Simple Router: created inside an actor explicitly, specifying the routing logic & managing routees by code
class Worker extends Actor with ActorLogging {
  override def receive: Receive = {
    case Master.Work =>
      log.info("Receive Work message")
  }
}

object Master {
  case object Work
}
class Master extends Actor {
  import Master._
  var router = {
    val routees = Vector.fill(5) { // create 5 Routees which will be watched and routed via this Router
      val actorRef = context.actorOf(Props[Worker]) // create a new worker actor
      // We watch the routees to be able to re-create them if they are terminated
      context.watch(actorRef)  // watch the created worker actor
      // We create the routees as ordinary child actors wrapped in ActorRefRoutee
      ActorRefRoutee(actorRef) // create a Routee that sends  messages to the created worker actor
    }
    // create a router and specify the routing logic (the router will be used to route messages to the Routee)
    Router(RoundRobinRoutingLogic(), routees)
  }
  override def receive: Receive = {
    // router.route(): send the messages to Routee via the Router
    //   i.e. not sending messages to Routee directly but through the Router
    case Work =>
      router.route(Work, sender)

    // re-create the Routee when receiving Terminated message
    case Terminated(worker) =>
      router = router.removeRoutee(worker)
      val actorRef = context.actorOf(Props[Worker])
      context.watch(actorRef)
      router = router.addRoutee(actorRef)
  }
}

// 1) A Router Actor: a self contained actor responsible for managing the routees
//      it loads routing logic and other settings from configuration
//    two distinct flavors:
//    i)  Pool:
//        The router creates routees as child actors and removes them from the router if they terminate
//    ii) Group:
//        The routee actors are created externally, i.e. create routees separately and provide them to the router for its use
//        The router sends messages to the specified path using actor selection, without watching for termination

// i) create router from a Pool:
class Master2 extends Actor {
  import Master._
  // 1) create the router from a Pool: load the Pool (& routing logic) and number of routees from configuration
  val router1: ActorRef = context.actorOf(FromConfig.props(Props[Worker]), "router1") // actor path = "/master2/router1"
  // 2) create the router actor: use the Pool (& routing logic) instance directly, and specify the Routee actor and its number of routees
  val router2: ActorRef = context.actorOf(RoundRobinPool(5).props(Props[Worker]), "router2")
  override def receive: Receive = {
    // router.route(): send the messages to Routee via the Router
    //   i.e. not sending messages to Routee directly but through the Router
    case Work =>
      router1 ! Work
      router2 ! Work
  }
}

class Routing extends TestKit(ActorSystem("Routing")) with FlatSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = { // as the class extends BeforeAndAfterAll, we can specify what to do after all tests
    system.terminate()
  }

  "Create a Router inside an actor which" can "be used to send/route messages to Routees" in {
    val master = system.actorOf(Props[Master], name = "master")
    val probe = TestProbe(name = "testProbe")
    probe.send(master, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$a] Receive Work message
    probe.send(master, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$b] Receive Work message
    probe.send(master, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$c] Receive Work message
    probe.send(master, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$d] Receive Work message
    probe.send(master, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$e] Receive Work message
    probe.send(master, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$a] Receive Work message
  }
  // 1) Stateful Routing Logic:
  //    RoundRobinRoutingLogic, SmallestMailboxRoutingLogic, etc.
  // 2) Stateless Routing Logic:
  //    RandomRoutingLogic, ConsistentHashingRoutingLogic, etc.

  // In order to make an actor to make use of an externally configurable router the FromConfig props wrapper must be used to denote that the actor accepts routing settings from configuration
  "A Router with RoundRobinLogic" can "send/route messages to Routees in sequence" in {
    val master2 = system.actorOf(Props[Master2], name = "master2") // actor path = "/master2"
    val probe2 = TestProbe(name = "testProbe2")
    probe2.send(master2, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$a] Receive Work message
    probe2.send(master2, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$b] Receive Work message
    probe2.send(master2, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$c] Receive Work message
    probe2.send(master2, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$d] Receive Work message
    probe2.send(master2, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$e] Receive Work message
    probe2.send(master2, Master.Work); Thread.sleep(100) // [akka://Routing/user/master/$a] Receive Work message

  }
}
