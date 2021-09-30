import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import akka.testkit.{EventFilter, ImplicitSender, TestKit}

// ref: https://doc.akka.io/docs/akka/current/fault-tolerance.html#supervision-of-top-level-actors
class FaultHandlingDocSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  // a Child class that allows you set and get its state
  class Child extends Actor {
    var state = 0
    def receive = {
      case ex: Exception => throw ex
      case x: Int        => state = x
      case "get"         => sender() ! state
    }
  }

  // a Supervisor class with customized strategy
  class Supervisor extends Actor {
    import akka.actor.OneForOneStrategy
    import akka.actor.SupervisorStrategy._
    import scala.concurrent.duration._

    override val supervisorStrategy =
      OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
        case _: ArithmeticException      => Resume
        case _: NullPointerException     => Restart
        case _: IllegalArgumentException => Stop
        case _: Exception                => Escalate
      }

    def receive = {
      case p: Props => sender() ! context.actorOf(p)
    }
  }

  // a Supervisor class with default strategy
  class SupervisorWithDefaultStrategy extends Actor {
    def receive = {
      case p: Props => sender() ! context.actorOf(p)
    }
  }

  def this() = this(ActorSystem("FaultHandlingDocSpec", ConfigFactory.parseString("""
    akka {
      loggers = ["akka.testkit.TestEventListener"]
      loglevel = "WARNING"
    }
    """
  )))

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val supervisor: ActorRef = system.actorOf(Props(new Supervisor()), "supervisor")
  val supervisorWithDefaultStrategy: ActorRef = system.actorOf(Props(new SupervisorWithDefaultStrategy()), "supervisorWithDefaultStrategy")

  "A supervisor" must {
    "apply the chosen strategy for its child" in {
      supervisor ! Props(new Child()) // send message to supervisor so that it creates a child actor and responds with the child ActorRef
      val child: ActorRef = expectMsgType[ActorRef] // retrieve answer from TestKit’s testActor

      child ! 42 // set state to 42
      child ! "get"
      expectMsg(42)

      child ! new ArithmeticException // crash the child actor, and we expect that supervisor will Resume the child actor
      child ! "get"
      expectMsg(42)

      child ! new NullPointerException // crash the child actor harder, and we expect that supervisor will Restart the child actor
      child ! "get"
      expectMsg(0) // state = 0 as the child actor is restarted

      watch(child) // have testActor watch “child”
      child ! new IllegalArgumentException // break the child actor, and we expect that supervisor will Stop the child actor
      expectMsgPF() { case Terminated(`child`) => () } // expecting Terminated message as we are watching the stopped child
    }

    "apply default strategy for its child" in {
      supervisorWithDefaultStrategy ! Props(new Child()) // send message to supervisor so that it creates a child actor and responds with the child ActorRef
      val child: ActorRef = expectMsgType[ActorRef] // retrieve answer from TestKit’s testActor

      child ! 42 // set state to 42
      child ! "get"
      expectMsg(42)

      child ! new ArithmeticException // crash the child actor, and we expect that supervisor will Restart the child actor for all Exceptions
      child ! "get"
      expectMsg(0)

      child ! 42 // set state to 42
      child ! "get"
      expectMsg(42)

      child ! new NullPointerException // crash the child actor harder, and we expect that supervisor will Restart the child actor for all Exceptions
      child ! "get"
      expectMsg(0) // state = 0 as the child actor is restarted

      watch(child) // have testActor watch “child”
      child ! new IllegalArgumentException // crash the child actor harder, and we expect no message replied as the child actor is Restarted
      expectNoMessage()
    }
  }
}