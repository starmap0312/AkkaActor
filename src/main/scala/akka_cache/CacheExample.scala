package akka_cache

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.http.scaladsl.server.Directives._
import scala.io.StdIn
import scala.util.{Failure, Success}
import akka.http.scaladsl.server.directives.CachingDirectives._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.HttpMethods

// Reference: https://doc.akka.io/docs/akka-http/10.1.1/routing-dsl/directives/caching-directives/index.html
object CacheExample extends App {
  implicit val system = ActorSystem()
  import system.dispatcher

  // Created outside the route to allow using the same cache across multiple calls
  val myCache = routeCache[Uri]
  // this creates an LfuCache with key type of Uri

  // Example keyer for GET requests
  val simpleKeyer: PartialFunction[RequestContext, Uri] = {
    case r: RequestContext if r.request.method == HttpMethods.GET => {
      r.request.uri
    }
  }

  var i = 0
  var j = 0
  val route: Route =
    get {
      path("cached") {
        // cache([cache], [keyer]): serve the request from the given cache
        // if the key is not found, then run the inner route to generate a new response
        // note: this returns the cache result for all "new-connection" requests
        cache(myCache, simpleKeyer) {
          complete {
            i += 1
            i.toString
          }
        }
      } ~
        path("alwaysCache") {
          // note: this returns the cache result for all requests
          alwaysCache(myCache, simpleKeyer) {
            complete {
              j += 1
              j.toString
            }
          }
        }
    }

  Http().bindAndHandleAsync(Route.asyncHandler(route), "localhost", 9000).
    onComplete {
      case Success(_) =>
        println("http://localhost:9000/cache, http://localhost:9000/alwaysCache")
        StdIn.readLine()
        system.terminate()
      case Failure(ex) =>
        println("Binding failed")
        ex.printStackTrace()
        system.terminate()
    }
}
