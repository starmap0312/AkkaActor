package akka_extensions

import java.util.concurrent.atomic.AtomicLong
import akka.actor.Extension
import akka.actor.ActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem

class CountExtensionImpl extends Extension {
  //Since this Extension is a shared instance per ActorSystem we need to be thread-safe
  private val counter = new AtomicLong(0)

  //This is the operation this Extension provides
  def increment() = counter.incrementAndGet()
}

object CountExtension extends ExtensionId[CountExtensionImpl] with ExtensionIdProvider {
  //The lookup method is required by ExtensionIdProvider, so we return ourselves here, this allows us
  // to configure our extension to be loaded when the ActorSystem starts up
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
  println(CountExtension(system).increment) // 1
  println(CountExtension(system).increment) // 2
  system.terminate()
}