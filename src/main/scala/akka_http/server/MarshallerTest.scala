package akka_http.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.Done
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.server.Directives._
//import akka.http.scaladsl.server.Directives.{get, pathPrefix, onSuccess, complete, LongNumber}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
//import akka.http.scaladsl.model.Uri.Path.{/}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import spray.json.DefaultJsonProtocol._
//import spray.json.DefaultJsonProtocol.{jsonFormat1, jsonFormat2}

import scala.concurrent.Future
import scala.io.StdIn

// Set up a web-server that communicates with a mock database
// 1) marshalling and unmarshalling JSON in this example is provided by the “spray-json” library
//    it marshals Item returned from database to a json, and vice versa
// 2) one of the strengths of Akka HTTP is that streaming data is at its heart:
//    i.e. both request and response bodies can be streamed through the server achieving constant memory usage
//         even for very large requests or responses
//    streaming responses is back-pressured by remote client so that server will not push data faster than client can handle
//    streaming requests means that server decides how fast remote client can push the data of the request body
//      ex. curl --limit-rate 50b localhost:8080/random (use curl command with option limit-rate to mimic a slow client)
object MarshallerTest {

  implicit val system = ActorSystem()             // needed to run the route
  implicit val materializer = ActorMaterializer() // needed for the future map/flatmap in the end and future in fetchItem and saveOrder
  implicit val executionContext = system.dispatcher

  var orders: List[Item] = List(Item("item1", 1), Item("item2", 2))

  // domain model
  final case class Order(items: List[Item])
  final case class Item(name: String, id: Long)

  // formats for unmarshalling and marshalling (the following two implicits are temporal coupled)
  implicit val itemFormat = jsonFormat2(Item)
  implicit val orderFormat = jsonFormat1(Order)

  // (fake) async database query api
  def fetchItem(itemId: Long): Future[Option[Item]] = Future {
    orders.find(o => o.id == itemId)
  }

  def saveOrder(order: Order): Future[Done] = {
    orders = order match {
      case Order(items) => items ::: orders
      case _            => orders
    }
    Future { Done }
  }

  def main(args: Array[String]): Unit = {

    val route: Route = {
      get {
        pathPrefix("fetch" / LongNumber) { id => // getting an Item id will marshal Item(name: String, id: Long) to a json map

          val maybeItem: Future[Option[Item]] = fetchItem(id)

          onSuccess(maybeItem) {
            case Some(item) => complete(item) // ex. { name: "item1", id: 1 }
            case None => complete(StatusCodes.NotFound) // there might be no item for a given id
          }
        }
      } ~ post {
        path("save") {
          entity(as[Order]) { order => // posting a json Order list will be unmarshalled to an Order(items: List[Item])
            val saved: Future[Done] = saveOrder(order)
            onComplete(saved) { done =>
              complete("order created")
            }
          }
        }
      }
    }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server running at http://localhost:8080/\n" +
      s"http://localhost:8080/fetch/1\n" +
      s"Press RETURN to stop...")
    StdIn.readLine() // let it run until user presses return

    bindingFuture
      .flatMap(_.unbind())                // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done

  }
}
