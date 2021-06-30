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
// HttpServerActor: the actual actor that runs the http service
class ServerActor() extends Actor {
  implicit val system = context.system
  import system.dispatcher
  implicit val materialized = ActorMaterializer()
  import ServerActor._

  def receive = {
    case StartServer => // the actor is waiting for StartServer message
      val s: ActorRef = sender()
      println("StartServer: bindAndHandle: http://localhost:9001/hello")
      val bindFuture = Http().bindAndHandle(route, "localhost", 9001) // once the actor receives the message, it binds to a local port and handles the defined route
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

object ServerExtension extends ExtensionId[Server] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = ServerExtension
  override def createExtension(system: ExtendedActorSystem): Server = new Server()(system) // called by Akka to instantiate our Extension
}

// define a class that can start() an ServerActor, i.e. instantiate a ServerActor and send a StartServer message to it
// note that we define the class as an akka Extension, which will then be "loaded once" per ActorSystem (a shared instance in within an ActorSystem) and managed by Akka
// in other words,
class Server()(implicit val system: ExtendedActorSystem) extends akka.actor.Extension {
  implicit val timeout = Timeout(10.second)
  def start() = {
    val serverActor = Some(system.actorOf(Props(new ServerActor()), s"server-actor"))
    serverActor.map(_ ? ServerActor.StartServer).get
  }
}

object SimpleServer {
  implicit val system = ActorSystem("SimpleServer")
  val serverExtension = ServerExtension(system) // the extension Implementation is tied to a single ActorSystem, shared within that ActorSystem

  def main(args: Array[String]): Unit = {
    // http://127.0.0.1:9001/hello
    serverExtension.start()
    // serverExtension.start() // you cannot run the server twice: Exception InvalidActorNameException: actor name [server-actor] is not unique
  }
}