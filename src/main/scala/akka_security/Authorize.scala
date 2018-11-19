package akka_security

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka_http.server.CsvStreaming.{route, system}

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success}

case class User(name: String)

object Authorize extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // authenticate the user:
  def MyUserAuthenticator(credentials: Credentials): Option[User] = credentials match {
    case Credentials.Provided(id) => Some(User(id))
    case _                        => None
  }
  // check if user is authorized to perform admin actions:
  val admins = Set("Peter")
  def hasAdminPermissions(user: User): Boolean = admins.contains(user.name)

  val route = {
    Route.seal {
      // 1) authorize:
      authenticateBasic(realm = "secure site", MyUserAuthenticator) { user =>
        path("peters-lair") {
          authorize(hasAdminPermissions(user)) {
            complete(s"'${user.name}' visited Peter's lair")
            // ex. curl -u John:123 http://localhost:9001/peters-lair
            // it returns:
            //   The supplied authentication is not authorized to access this resource
            // ex. curl -u Peter:123 http://localhost:9001/peters-lair
            // it returns:
            //   'Peter' visited Peter's lair
          }
        }
      } ~ {
        // 2) extractCredentials:
        extractCredentials { creds =>
          path("peters-creds") {
            complete {
              creds match {
                case Some(c) => "Credentials: " + c
                case _ => "No credentials"
              }
            }
            // ex. curl http://localhost:9001/peters-creds
            // it returns:
            //   No credentials
            // ex. curl -u Peter:123 http://localhost:9001/peters-credsds
            // it returns:
            //   Credentials: Basic UGV0ZXI6MTIz
          }
        }
      }
    }
  }
  val config = system.settings.config.getConfig("app")
  val interface = config.getString("interface")
  val port = config.getInt("port")

  val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandle(route, interface, port)
  bindingFuture onComplete {
    case Success(binding) => println(s"Server is listening on port: ${binding.localAddress.getPort}")
    case Failure(ex) => println(s"Binding fails with error: ${ex.getMessage}")
  }
  StdIn.readLine()
  system.terminate()

}
