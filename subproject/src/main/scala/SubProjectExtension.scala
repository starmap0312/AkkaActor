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
class SubProjectExtensionImpl extends Extension {
  val logger = LoggerFactory.getLogger(getClass.getName)
  logger.info("SubProjectExtensionImpl is instantiated")
  println("SubProjectExtensionImpl is instantiated")

  //Since this Extension is a shared instance per ActorSystem we need to be thread-safe
  private val counter = new AtomicLong(0)

  //This is the operation this Extension provides
  def increment() = counter.incrementAndGet()
}

object SubProjectExtension extends ExtensionId[SubProjectExtensionImpl] with ExtensionIdProvider {
  val logger = LoggerFactory.getLogger(getClass.getName)
  logger.info("SubProjectExtension is instantiated")
  println("SubProjectExtension is instantiated")
  //The lookup method is required by ExtensionIdProvider, so we return ourselves here
  // loaded through Akka configuration: this allows us to configure our extension to be loaded when the ActorSystem starts up
  override def lookup = SubProjectExtension

  //This method will be called by Akka to instantiate our Extension
  override def createExtension(system: ExtendedActorSystem) = new SubProjectExtensionImpl

  /**
    * Java API: retrieve the Count extension for the given system.
    */
  override def get(system: ActorSystem): SubProjectExtensionImpl = super.get(system)
}