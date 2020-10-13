package subproject.subtask

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object SubTask extends App {
  val system = ActorSystem("subtask", ConfigFactory.load())

  println("I am a task in the sub-project")
  system.terminate()
}