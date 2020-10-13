import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.CircuitBreaker
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import akka.pattern.ask
import akka.pattern.pipe
import org.scalatest.flatspec.AnyFlatSpec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should

class UserService2 extends Actor {
  val info = Map("john" -> 23, "tom" -> 30)
  override def receive: Receive = {
    case Retriever.Get(user: String) =>
      sender ! info.get(user)
  }
}

object Retriever {
  case class Get(user: String)
  def props() = Props(new UserService2)
}
class Retriever(userService: ActorRef) extends Actor with ActorLogging {
  import Retriever._
  implicit val timeout = Timeout(3.seconds) // timeout of asking userService actor

  val cb = CircuitBreaker(
    context.system.scheduler,
    maxFailures = 3,
    callTimeout = 1.second,
    resetTimeout = 30.seconds
  )

  override def receive: Receive = {
    case Get(user) =>
      // use the circuit breaker to send message to other actors, so that circuit will be open if overloaded
      val response = cb.withCircuitBreaker(userService ? user).mapTo[String]
      response pipeTo sender
  }
}

class Responsiveness extends AnyFlatSpec with should.Matchers {
  implicit val system = ActorSystem("Responsiveness")

  "Circuit Breaker" can "be used to provide resilience for overloaded systems" in {
    val retriever = system.actorOf(Retriever.props(), "retriever")
    val probe = TestProbe("testProbe")
    probe.send(retriever, Retriever.Get("john"))
    probe.expectMsg(Some(23))
    probe.send(retriever, Retriever.Get("tom"))
    probe.expectMsg(Some(30))
    probe.send(retriever, Retriever.Get("unknown"))
    probe.expectMsg(None)

  }
}