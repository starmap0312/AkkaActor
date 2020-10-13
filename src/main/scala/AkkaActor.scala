import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

object Akkaactor extends App {
  val logger = LoggerFactory.getLogger(getClass.getName)
  logger.debug("Debug message")
  logger.info("Default executable for docker image") // [main] INFO Akkaactor$ - Default executable for docker image

  // note: it is required instantiate an ActorSystem to create the CountExtension
  val system = ActorSystem("akaactor", ConfigFactory.load())
  println("an ActorSystem is instantiated")
  system.terminate()
}
