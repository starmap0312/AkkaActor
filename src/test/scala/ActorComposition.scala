import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask  // this is needed to call the ask() method of (actorRef ? Message)
import akka.util.Timeout // this is needed to define implicit timeout for (actorRef ? Message)
import akka.pattern.pipe // this is needed to call the pipeTo() method of (future pipeTo actorRef)
import scala.concurrent.ExecutionContext.Implicits.global // this is needed to use (future pipeTo actorRef)
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}

import scala.concurrent.duration._
import scala.util.Failure

// 1) The Customer Pattern
class Receiver extends Actor with ActorLogging {
  override def receive: Receive = {
    case msg =>
      log.info("receive {} from sender {}", msg, sender())
      // [akka://TestProbe/user/receiver] receive hi from sender Actor[akka://TestProbe/system/testProbe-2#985951655]
  }
}

object Auditor {
  def props(target: ActorRef): Props = Props(new Auditor(target))
}
class Auditor(target: ActorRef) extends Actor with ActorLogging {
  // the audit actor audits the message and then forwards the to a target actor who actually handles the message
  // similarly, this actor can behave like a supervisor (monitor), or an access-controller, etc.
  override def receive: Receive = {
    case msg =>
      log.info("forward {} to {}", msg, target)
      // [akka://TestProbe/user/auditor] forward hi to Actor[akka://TestProbe/user/receiver#2135441197]
      target forward msg
  }
}

// 2) The Ask Pattern
object PostOffice {
  case class Get(mail: String)
  def props(userService: ActorRef): Props = Props(new PostOffice(userService))
}
class PostOffice(userService: ActorRef) extends Actor {
  import PostOffice._
  implicit val timeout = Timeout(3.seconds) // timeout of asking userService actor

  override def receive: Receive = {
    case Get(mail) =>
      (userService ? MailService.FindMail()). // ask returns a Future
        mapTo[MailService.Mails]. // mapTo maps Future[Any] to some type Future[UserInfo]
        map(info => info.mails.filter(_ == mail)). // map UserInfo to a List of mails
        recover { case ex => Failure(ex) }. // if the Future fails with exception, recover it to a Failure
        pipeTo(sender) // pipeTo() tells the sender a [value] in case of Success([value])
                       // or it tells the sender a Status.Failure([exception]) in case of Failure([exception])
    // or we can aggregate messages from multiple actors
    //case Get(mail, user) =>
    //  val response = for {
    //    mails <- (mailService ? mail).mapTo[Mails]
    //    users <- (userService ? user).mapTo[Users]
    //  } yield {
    //    Result(mails, users)
    //  }
    //  response pipeTo sender
  }
}

object MailService {
  case class FindMail()
  case class Mails() {
    val mails: List[String] = List("mail1", "mail2", "mail3", "mail4")
  }
}
class MailService extends Actor {
  import MailService._
  override def receive: Receive = {
    case FindMail() =>
      sender ! Mails()
  }
}

class ActorComposition extends TestKit(ActorSystem("TestProbe")) with FlatSpecLike with BeforeAndAfterAll {
  // the test class has a ActorSystem, i.e. system, as its constructor parameter

  override def afterAll(): Unit = { // as the class extends BeforeAndAfterAll, we can specify what to do after all tests
    system.terminate()
  }

  "In the Customer Pattern, one" can "define an actor to forward message to another actor" in {
    val receiver = system.actorOf(Props[Receiver], name = "receiver")
    val auditor = system.actorOf(Auditor.props(receiver), name = "auditor")
    val probe = TestProbe(name = "testProbe")
    probe.send(auditor, "hi")
  }

  "In the Ask Pattern, one" can "ask an actor for some message and pipeTo the original sender" in {
    val userService = system.actorOf(Props[MailService], name = "userService")
    val postOffice = system.actorOf(PostOffice.props(userService), name = "postOffice")
    val user = TestProbe(name = "user")
    user.send(postOffice, PostOffice.Get("mail2"))
    user.expectMsg(List("mail2"))
  }

}