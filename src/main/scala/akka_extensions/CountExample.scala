package akka_extensions

import java.util.concurrent.atomic.AtomicLong
import akka.actor.Extension
import akka.actor.ActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem

// If you want to add features to Akka, define ExtensionId class with its implementation
// Extensions will only be "loaded once" per ActorSystem (a shared instance in within an ActorSystem) and managed by Akka
class CountExtension extends Extension {
  //Since this Extension is a shared instance per ActorSystem we need to be thread-safe
  private val counter = new AtomicLong(0)

  //This is the operation this Extension provides
  def increment() = counter.incrementAndGet()
  println("CountExtension is instantiated")
}

object CountExtensionImpl extends ExtensionId[CountExtension] with ExtensionIdProvider {
  //The lookup method is required by ExtensionIdProvider, so we return ourselves here, this allows us
  // to configure our extension to be loaded when the ActorSystem starts up
  override def lookup = CountExtensionImpl

  //This method will be called by Akka to instantiate our Extension
  override def createExtension(system: ExtendedActorSystem) = new CountExtension

  /**
    * Java API: retrieve the Count extension for the given system.
    */
  override def get(system: ActorSystem): CountExtension = super.get(system)
}

object CountExample extends App {
  val system = ActorSystem("CountExample")
  CountExtensionImpl.get(system) // a CountExtension singleton is instantiated and shared within the system
  CountExtensionImpl.get(system) // no more CountExtension is instantiated
  println(CountExtensionImpl(system).increment) // 1
  // note you don't need to call the above get() to instantiate the Extension, you can simply use CountExtensionImpl(system) that is tied to a single ActorSystem, shared within that ActorSystem
  println(CountExtensionImpl(system).increment) // 2
  system.terminate()
}