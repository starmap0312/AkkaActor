package akka_spray_json

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.server.Directives._

import scala.io.StdIn

// domain model
case class Item(name: String, id: Long) // {name: "book", id: 42}
case class Order(items: List[Item])

// define a trait that takes care of implicit marshalling/un-marshalling between Json and model classes
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val itemFormat = jsonFormat2(Item)   // Json String <-> Item
  implicit val orderFormat = jsonFormat1(Order) // Json String <-> Order
}

object Marshalling extends App with JsonSupport {
  implicit val system = ActorSystem("my-webserver")
  implicit val materializer = ActorMaterializer()   // needed for the future flatMap/onComplete in the end (i.e. val bindingFuture)
  implicit val executionContext = system.dispatcher

  val route = {
    pathSingleSlash {
      get {
        complete(Item("book", 42)) // marshall (serialize) Item to a JSON String: {name: "book", id: 42}
      } ~
      post {
        entity(as[Order]) { order => // un-marshall (deserialize) JSON String to Order class
          val count = order.items.size
          val names = order.items.map(_.name).mkString(", ")
          complete(s"$count items: $names")
          // ex. curl -X POST -d '{"items":[{"name":"book1","id":1}, {"name":"book2","id":2}]}' \
          //       -H "Content-Type: application/json" "http://localhost:9000"
          // this returns:
          //     2 items: book1, book2
        }
      }
    }
  }
  val bindingFuture = Http().bindAndHandle(route, "localhost", 9000)

  println(s"Server at http://localhost:9000/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return

  bindingFuture
    .flatMap(_.unbind())                 // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
