import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.TestProbe
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.concurrent.duration._

class Toggle extends Actor {
  def happy: Receive = {
    case msg @ "hi" =>
      println(msg)
      sender ! "happy"
      context.become(sad)
  }
  def sad: Receive = {
    case msg @ "hi" =>
      println(msg)
      sender ! "sad"
      context.become(happy)
  }
  override def receive: Receive = happy
}

class TestProbe extends AnyFlatSpec with should.Matchers {
  implicit val system = ActorSystem("TestProbe")
  // the test class has a ActorSystem, i.e. system, as its constructor parameter

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
