import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest._

import scala.concurrent.duration._

class Toggle extends Actor {
  def happy: Receive = {
    case "hi" =>
      sender ! "happy"
      context.become(sad)
  }
  def sad: Receive = {
    case "hi" =>
      sender ! "sad"
      context.become(happy)
  }
  override def receive: Receive = happy
}

class TestProbe extends TestKit(ActorSystem("TestProbe")) with FlatSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = { // as the class extends BeforeAndAfterAll, we can specify what to do after all tests
    system.terminate()
  }

  "A TestProbe" can "interact with an Actor by sending and expecting messages" in {
    val toggle = system.actorOf(Props[Toggle], "toggle")
    val probe = TestProbe()
    probe.send(toggle, "hi")
    probe.expectMsg("happy")
    probe.send(toggle, "hi")
    probe.expectMsg("sad")
    probe.send(toggle, "unknown")
    probe.expectNoMessage(1.seconds)
  }
}
