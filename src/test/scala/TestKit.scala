import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.TestProbe
import org.scalatest.{FlatSpec, MustMatchers, WordSpecLike}

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

class TestKit extends FlatSpec {
  "A TestProbe" should "receive messages it expect" in {
    implicit val system = ActorSystem("TestProbe")
    val toggle = system.actorOf(Props[Toggle], "toggle")
    val probe = TestProbe()
    probe.send(toggle, "hi")
    probe.expectMsg("happy")
    system.terminate()

  }
}
