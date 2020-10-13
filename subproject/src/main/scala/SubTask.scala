package subproject.subtask

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object SubTask extends App {
  println("I am a task in the sub-project")

  // note: it is required instantiate an ActorSystem to create the SubProjectExtension
  val system = ActorSystem("subtask", ConfigFactory.load())
  println("an ActorSystem is instantiated")
  system.terminate()
}