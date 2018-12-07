package akka_stream

import akka.actor.ActorSystem

object SourceQueueExample extends App {
  val system = ActorSystem("SourceQueueExample")

  system.terminate()
}
