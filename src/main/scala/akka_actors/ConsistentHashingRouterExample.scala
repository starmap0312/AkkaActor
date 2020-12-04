package akka_actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.routing.ConsistentHashingPool
import akka.routing.ConsistentHashingRouter.{ConsistentHashMapping, ConsistentHashable, ConsistentHashableEnvelope}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

// https://doc.akka.io/docs/akka/current/routing.html#consistenthashingpool-and-consistenthashinggroup
// There are 3 ways to define what data to use for the consistent hash key.
// 1) define hashMapping to map incoming messages to their consistent hash key: this makes the decision transparent for the sender
// 2) make the message class implement ConsistentHashable: not transparent for the sender but it can be defined together with the message definition.
// 3) wrap messages in ConsistentHashableEnvelope: this makes the decision transparent for the sender

object Cache {
  final case class Evict(key: String)
  // 2) make the message class implement ConsistentHashable
  final case class Get(key: String) extends ConsistentHashable {
    override def consistentHashKey: Any = key
  }
  final case class Entry(key: String, value: String)
}
class Cache extends Actor {
  import Cache._

  var cache = Map.empty[String, String]

  def receive = {
    case Entry(key, value) =>
      println(s"add Entry(${key}, ${value}) to cache ${cache}")
      println(context)
      cache += (key -> value)
    case Get(key)          => sender() ! cache.get(key)
    case Evict(key)        => cache -= key
  }
}
class ConsistentHashingRouter extends Actor {
  import Cache._

  def hashMapping: ConsistentHashMapping = {
    case Evict(key) => key // this
  }

  val cache: ActorRef = context.actorOf(ConsistentHashingPool(2, hashMapping = hashMapping).props(Props[Cache]()), name = "cache")

  override def receive: Receive = {
    case "add" =>
      // 3) wrap messages in ConsistentHashableEnvelope
      cache forward ConsistentHashableEnvelope(message = Entry("hello", "HELLO"), hashKey = "hello") // add Entry(hello, HELLO) to cache Map() -> new routee
      cache forward ConsistentHashableEnvelope(message = Entry("hi", "HI"), hashKey = "hi") // add Entry(hi, HI) to cache Map() -> new routee
      cache forward ConsistentHashableEnvelope(message = Entry("hi", "HI"), hashKey = "need-resize-1") // add Entry(hi, HI) to cache Map(hi -> HI) -> reuse old routee!
      cache forward ConsistentHashableEnvelope(message = Entry("hi", "HI"), hashKey = "need-resize-2") // add Entry(hi, HI) to cache Map(hello -> HELLO) -> reuse old routee!
      cache forward ConsistentHashableEnvelope(message = Entry("hi", "HI"), hashKey = "need-resize-3") // add Entry(hi, HI) to cache Map(hello -> HELLO, hi -> HI) -> reuse old routee!
    case msg: String =>
      cache forward Get(msg)
  }
}

object ConsistentHashingRouterExample extends App {
  implicit val system = ActorSystem("ConsistentHashingRouterExample")
  implicit val dispatcher = system.dispatcher
  implicit val timeout = Timeout(1.seconds)

  val routerRef = system.actorOf(Props[ConsistentHashingRouter], "ConsistentHashingRouter")
  routerRef ! "add"
  (routerRef ? "hello").foreach(println(_)) // Some(HELLO)
  (routerRef ? "hello").foreach(println(_)) // Some(HELLO)
  Thread.sleep(3000)
  (routerRef ? "hi").foreach(println(_)) // Some(HI)
  (routerRef ? "unknown").foreach(println(_)) // None

  Thread.sleep(3000)
  system.terminate()
}