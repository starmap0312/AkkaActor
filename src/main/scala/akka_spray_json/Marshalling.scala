package akka_spray_json

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.ActorMaterializer
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import akka.http.scaladsl.server.Directives._
import spray.json.DefaultJsonProtocol._ // this is required for implicit conversion defined in companion object

import scala.io.StdIn

// 1) define domain models
case class Item(name: String, id: Long) // {name: "book", id: 42}
case class Order(items: List[Item])

// 1) define serialization/de-serialization of domain models in a trait
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  // this trait that takes care of implicit marshalling/un-marshalling between Json and model classes
  // extends the trait wherever json (un)marshalling is needed (this makes implicit method defined in the scope)
  implicit def itemFormat: RootJsonFormat[Item] = jsonFormat2(Item)    // Json String <-> Item
  implicit def orderFormat: RootJsonFormat[Order] = jsonFormat1(Order) // Json String <-> Order
}

// 2) define domain models
case class Item2(name: String, id: Long) // {name: "book", id: 42}
// 2) define serialization/de-serialization of domain models in companion object
object Item2 {
  // compiler will try to find implicit conversion from Item2 to String (or vice versa) in the companion object
  implicit def itemFormat: RootJsonFormat[Item2] = jsonFormat2(Item2.apply)    // Json String <-> Item
}
case class Order2(items: List[Item2])
object Order2 {
  // compiler will try to find implicit conversion from Order2 to String (or vice versa) in the companion object
  implicit def orderFormat: RootJsonFormat[Order2] = jsonFormat1(Order2.apply)    // Json String <-> Item
}

object Marshalling extends App with JsonSupport {
  implicit val system = ActorSystem("my-webserver")
  implicit val materializer = ActorMaterializer()   // needed for the future flatMap/onComplete in the end (i.e. val bindingFuture)
  implicit val executionContext = system.dispatcher

  val route = {
    pathSingleSlash {
      get {
        complete(Item("book", 42)) // marshall (serialize) Item to a JSON String
        // ex. curl http://localhost:9000/
        // this returns:
        //   {name: "book", id: 42}
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
    } ~
    pathPrefix("example2") {
      get {
        complete(Item2("book", 43))
        // ex. curl http://localhost:9000/example2
        // this returns:
        //   {name: "book", id: 43}
      } ~
        post {
          entity(as[Order2]) { order => // un-marshall (deserialize) JSON String to Order class
            val count = order.items.size
            val names = order.items.map(_.name).mkString(", ")
            complete(s"$count items: $names")
            // ex. curl -X POST -d '{"items":[{"name":"book1","id":1}, {"name":"book2","id":2}]}' \
            //       -H "Content-Type: application/json" "http://localhost:9000/example2"
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
