package akka_server

import akka.actor.{Actor, ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, PoisonPill, Props}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

// HttpServerActor: the actual actor that runs the http service
object ServerActor {
  case object StartServer
  case object StopServer
  val route: Route = {
    get {
      path("hello") { // only /path is handled, but not /path/
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>World</h1>")) // Hello World
      } ~ complete("api not defined")
    }
  }
}
class ServerActor() extends Actor {
  implicit val system = context.system
  import system.dispatcher
  implicit val materialized = ActorMaterializer()
  import ServerActor._

  def receive = {
    case StartServer => // the actor is waiting for StartServer message
      val s: ActorRef = sender()
      val bindFuture = Http().bindAndHandle(route, "localhost", 9001)
      bindFuture.onComplete(x => s ! x)
      context.become { // when the server starts, the actor becomes to wait for StopServer message
        case StopServer =>
          val s: ActorRef = sender()
          bindFuture.flatMap(_.unbind()).onComplete {
            case x => self ! PoisonPill // terminate the Actor itself permanently (a message that all Actors understand)
          }
      }
  }
}

object ServerExtensionImpl extends ExtensionId[ServerExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = ServerExtensionImpl
  override def createExtension(system: ExtendedActorSystem): ServerExtension = new ServerExtension()(system) // called by Akka to instantiate our Extension
}

class ServerExtension()(implicit val system: ExtendedActorSystem) extends akka.actor.Extension {
  implicit val timeout = Timeout(10.second)
  def start() = {
    val serverActor = Some(system.actorOf(Props(new ServerActor()), s"server-actor"))
    serverActor.map(_ ? ServerActor.StartServer).get
  }
}

object SimpleServer {
  implicit val system = ActorSystem("SimpleServer")
  val serverExtension = ServerExtensionImpl(system)

  def main(args: Array[String]): Unit = {
    // http://127.0.0.1:9001/hello
    serverExtension.start()
  }
}