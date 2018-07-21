package akka_actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive

// 1) an Actor is backed by by a single-thread
//   i) messages are received and processed by an Actor sequentially
//      i.e. blocking is replaced by enqueueing messages
//  ii) processing a message is an atomic unit of execution
// iii) message ordering:
//      if an actor sends multiple messages to the same actor, then they will NOT arrive out of order
// 2) message delivery guarantees:
//   i) at-most-once: (used by Akka, with the highest performance, i.e. fire-and-forget)
//      sending at-most-once,  i.e. delivers [0, 1] times
//  ii) at-least-once: (need to implement sender business logic to retry if not receiving ACK from receiver after some time)
//      sending at-least-once, i.e. delivers [1, infinity] times
// iii) exactly-once: (most expensive, with the worst performance, receiver needs to keep state to filter out duplicate deliveries)
//      sending exactly-once,  i.e. delivers 1 time
object BankAccountExample extends App {

  object BankAccount {
    case class Deposit(amount: BigInt) {  // define Message type in the companion object of the Actor class
      require(amount > 0)
    }
    case class Withdraw(amount: BigInt) { // define Message type in the companion object of the Actor class
      require(amount > 0)
    }
    case object Success                   // define Message object in the companion object of the Actor class
    case object Failure                   // define Message object in the companion object of the Actor class
  }
  class BankAccount extends Actor {
    import BankAccount._            // this makes the message types Failure not ambiguous with scala.util.Failure
    var balance: BigInt = BigInt(0) // internal state

    override def receive: Receive = LoggingReceive {
      case Deposit(amount) =>
        balance += amount
        sender ! Success // send ack message to sender when deposit is successful
      case Withdraw(amount) if amount <= balance =>
        balance -= amount
        sender ! Success
      case _ =>
        sender ! Failure
    }
  }

  object WireTransfer {
    case class Transfer(from: ActorRef, to: ActorRef, amount: BigInt)
    case object Done
    case object Incomplete
  }
  class WireTransfer extends Actor {
    import WireTransfer._

    override def receive: Receive = LoggingReceive {
      case Transfer(from, to, amount) =>
        from ! BankAccount.Withdraw(amount)
        context.become(awaitWithdraw(to, amount, sender))
    }

    def awaitWithdraw(to: ActorRef, amount: BigInt, client: ActorRef): Receive = LoggingReceive {
      case BankAccount.Success =>
        to ! BankAccount.Deposit(amount)
        context.become(awaitDeposit(client))
      case BankAccount.Failure =>
        client ! Incomplete
        context.stop(self)
    }

    def awaitDeposit(client: ActorRef): Receive = LoggingReceive {
      case BankAccount.Success =>
        client ! Done
        context.stop(self)
    }
  }

  class Client extends Actor {
    val account1 = context.actorOf(Props[BankAccount], "account1")
    val account2 = context.actorOf(Props[BankAccount], "account2")

    account1 ! BankAccount.Deposit(100)

    override def receive: Receive = LoggingReceive {
      case BankAccount.Success =>
        val wire = context.actorOf(Props[WireTransfer], "wire")
        wire ! WireTransfer.Transfer(account1, account2, 50)
        context.become(
          LoggingReceive {
            case WireTransfer.Done =>
              println("Transfer Done")
              context.stop(self)
          }
        )
    }
  }

  val system = ActorSystem("BankAccountExample")
  val task = system.actorOf(Props[Client], "mainTask")
  system.terminate()
}
