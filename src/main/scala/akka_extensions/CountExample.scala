package akka_extensions

import java.util.concurrent.atomic.AtomicLong

import akka.actor.Extension
import akka.actor.ActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import org.slf4j.LoggerFactory

// If you want to add features to Akka, define ExtensionId class with its implementation
// Extensions will only be "loaded once" per ActorSystem (a shared instance in within an ActorSystem) and managed by Akka
class CountExtensionImpl extends Extension {
  val logger = LoggerFactory.getLogger(getClass.getName)
  logger.info("CountExtensionImpl is instantiated")
  println("CountExtensionImpl is instantiated")

  //Since this Extension is a shared instance per ActorSystem we need to be thread-safe
  private val counter = new AtomicLong(0)

  //This is the operation this Extension provides
  def increment() = counter.incrementAndGet()
}

object CountExtension extends ExtensionId[CountExtensionImpl] with ExtensionIdProvider {
  val logger = LoggerFactory.getLogger(getClass.getName)
  logger.info("CountExtension is instantiated")
  println("CountExtension is instantiated")

  //The lookup method is required by ExtensionIdProvider, so we return ourselves here
  // loaded through Akka configuration: this allows us to configure our extension to be loaded when the ActorSystem starts up
  override def lookup = CountExtension

  //This method will be called by Akka to instantiate our Extension
  override def createExtension(system: ExtendedActorSystem) = new CountExtensionImpl

  /**
    * Java API: retrieve the Count extension for the given system.
    */
  override def get(system: ActorSystem): CountExtensionImpl = super.get(system)
}

object CountExample extends App {
  val system = ActorSystem("CountExample")

  // You can choose to have your Extension:
  // 1) loaded on-demand, ex.
  CountExtension.get(system) // a CountExtension singleton is demanded, thus instantiated and shared within the system
  CountExtension.get(system) // as it's shared within the ActorSystem, no new CountExtension is instantiated here
  println(CountExtension(system).increment) // 1
  // note you don't need to call the above get() to instantiate the Extension, you can simply use CountExtensionImpl(system) that is tied to a single ActorSystem, shared within that ActorSystem
  println(CountExtension(system).increment) // 2
  system.terminate()

  // 2) loaded at ActorSystem creation time through the Akka configuration
  //    ex. akka.extensions = ["akka_extensions.CountExtension"]

  // 3) Library extensions:
  // in its reference.conf: a third part library may register it’s extension for auto-loading on actor system startup
  // ex. akka.library-extensions += "akka_extensions.CountExtension"
  // note: there is no way to selectively remove such extensions, so this is only for the user would ever want it disabled, ex. in tests

}