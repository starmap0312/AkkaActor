import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}

import scala.concurrent.duration._

class Receiver extends Actor with ActorLogging {
  override def receive: Receive = {
    case msg =>
      log.info("receive {} from sender {}", msg, sender())
      // [akka://TestProbe/user/receiver] receive hi from sender Actor[akka://TestProbe/system/testProbe-2#985951655]
  }
}

object AuditTrail {
  def props(target: ActorRef): Props = Props(new AuditTrail(target))
}
class AuditTrail(target: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case msg =>
      log.info("forward {} to {}", msg, target)
      // [akka://TestProbe/user/forwarder] forward hi to Actor[akka://TestProbe/user/receiver#2135441197]
      target forward msg
  }
}

class ActorComposition extends TestKit(ActorSystem("TestProbe")) with FlatSpecLike with BeforeAndAfterAll {
  // the test class has a ActorSystem, i.e. system, as its constructor parameter

  override def afterAll(): Unit = { // as the class extends BeforeAndAfterAll, we can specify what to do after all tests
    system.terminate()
  }

  "In the Customer Pattern, one" can "define an actor to forward message to another actor" in {
    val receiver = system.actorOf(Props[Receiver], name = "receiver")
    val forwarder = system.actorOf(AuditTrail.props(receiver), name = "forwarder")
    val probe = TestProbe(name = "testProbe")
    probe.send(forwarder, "hi")
  }
}