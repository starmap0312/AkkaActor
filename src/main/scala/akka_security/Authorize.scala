package akka_security

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success}

case class User(name: String)

object Authorize extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // authenticate the user: type Authenticator[T] = Credentials => Option[T]
  def MyBasicAuthenticator(credentials: Credentials): Option[User] = credentials match {
    case Credentials.Provided(id) => Some(User(id))
    case _                        => None
  }

  // authenticate the user: type Authenticator[T] = Credentials => Option[T]
  def MyOAuth2Authenticator(credentials: Credentials): Option[String] = credentials match {
    case Credentials.Provided(token) => Some(token)
    case _                        => None
  }

  // check if user is authorized to perform admin actions:
  val admins = Set("Peter")
  def hasAdminPermissions(user: User): Boolean = admins.contains(user.name)

  val route = {
    Route.seal {
      // 1) authorize:
      authenticateBasic(realm = "secure site", MyBasicAuthenticator) { user =>
        path("peters-basic") {
          authorize(hasAdminPermissions(user)) {
            complete(s"'${user.name}' visited Peter's basic")
            // ex. curl -u John:123 http://localhost:9001/peters-basic
            // it returns:
            //   The supplied authentication is not authorized to access this resource
            // ex. curl -u Peter:123 http://localhost:9001/peters-basic
            // it returns:
            //   'Peter' visited Peter's basic
          }
        }
      } ~ {
        // 2) authenticateOAuth2:
        authenticateOAuth2(realm = "secure site2", MyOAuth2Authenticator) { token =>
            path("peters-oauth2") {
              complete(s"'visited Peter's oauth2 with credentials: ${token}")
            }
          // ex. curl http://localhost:9001/peters-oauth2
          // it returns:
          //  The resource requires authentication, which was not supplied with the request
          // ex. curl -H "Authorization: Bearer token_xxx" http://localhost:9001/peters-oauth2
          // it returns:
          //   'visited Peter's oauth2 with credentials: token_xxx
        }
      } ~ {
        // 2.1) authenticateOAuth2PF:
        authenticateOAuth2PF(realm = "secure site3", {
            case Credentials.Provided(token) => token
            case _ => None
          }) { token =>
            path("peters-oauth2-2") {
            complete(s"visited Peter's oauth2 with credentials: ${token}")
          }
          // ex. curl -H "Authorization: Bearer token_xxx" http://localhost:9001/peters-oauth2-2
          // it returns:
          //   'visited Peter's oauth2 with credentials: token_xxx
        }
      } ~ {
        // 3) extractCredentials:
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
