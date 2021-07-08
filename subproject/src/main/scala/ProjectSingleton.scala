package subproject.subtask
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
import akka.actor.Extension
import akka.actor.ActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem

// If you want to add features to Akka, define ExtensionId class with its implementation
// Extensions will only be "loaded once" per ActorSystem (a shared instance in within an ActorSystem) and managed by Akka
class ProjectSingleton extends Extension {
  val logger = LoggerFactory.getLogger(getClass.getName)
  logger.info("ProjectSingleton Extension is instantiated")
  println("ProjectSingleton Extension is instantiated")

  //Since this Extension is a shared instance per ActorSystem we need to be thread-safe
  private val counter = new AtomicLong(0)

  //This is the operation this Extension provides
  def increment() = counter.incrementAndGet()
}

object ProjectSingleton extends ExtensionId[ProjectSingleton] with ExtensionIdProvider {
  val logger = LoggerFactory.getLogger(getClass.getName)
  logger.info("ProjectSingleton ExtensionId is instantiated")
  println("ProjectSingleton ExtensionId is instantiated")
  //The lookup method is required by ExtensionIdProvider, so we return ourselves here
  // loaded through Akka configuration: this allows us to configure our extension to be loaded when the ActorSystem starts up
  override def lookup = ProjectSingleton

  //This method will be called by Akka to instantiate our Extension
  override def createExtension(system: ExtendedActorSystem) = new ProjectSingleton

  /**
    * Java API: retrieve the Count extension for the given system.
    */
  override def get(system: ActorSystem): ProjectSingleton = super.get(system)
}