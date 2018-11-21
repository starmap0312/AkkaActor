package akka_spray_json

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.stream.ActorMaterializer
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import ServerLog
import spray.json.DefaultJsonProtocol._

import scala.io.StdIn
import scala.util.Random

// 1) define serialization/de-serialization of domain models in a trait
// domain models
case class Item(name: String, id: Long) // {name: "book", id: 1}
case class Order(items: List[Item])
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  // this trait that takes care of implicit marshalling/un-marshalling between Json and model classes
  // extends the trait wherever json (un)marshalling is needed (this makes implicit method defined in the scope)
  implicit def itemFormat: RootJsonFormat[Item] = jsonFormat2(Item)    // Json String <-> Item
  implicit def orderFormat: RootJsonFormat[Order] = jsonFormat1(Order) // Json String <-> Order
}

// 2) define serialization/de-serialization of domain models in an object
// domain models
case class Item2(name: String, id: Long) // {name: "book", id: 2}
case class Order2(items: List[Item2])
object JsonSupport2 extends SprayJsonSupport with DefaultJsonProtocol {
  // this object defines implicit conversion methods, which can be imported as: import JsonSupport2._
  implicit def itemFormat: RootJsonFormat[Item2] = jsonFormat2(Item2)    // Json String <-> Item
  implicit def orderFormat: RootJsonFormat[Order2] = jsonFormat1(Order2) // Json String <-> Order
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()
}
// we can then use import to include the implict conversion methods to the scope
// i.e. import JsonSupport2._

// 3) define serialization/de-serialization of domain models in companion object
// domain models:
case class Item3(name: String, id: Long) // {name: "book", id: 3}
object Item3 {
  // compiler will try to find implicit conversion from Item3 to String (or vice versa) in the companion object
  implicit def itemFormat: RootJsonFormat[Item3] = jsonFormat2(Item3.apply)    // Json String <-> Item
}
case class Order3(items: List[Item3])
object Order3 {
  // compiler will try to find implicit conversion from Order3 to String (or vice versa) in the companion object
  implicit def orderFormat: RootJsonFormat[Order3] = jsonFormat1(Order3.apply)    // Json String <-> Item
}

object Marshalling extends App with JsonSupport {
  implicit val system = ActorSystem("my-webserver")
  implicit val materializer = ActorMaterializer()   // needed for the future flatMap/onComplete in the end (i.e. val bindingFuture)
  implicit val executionContext = system.dispatcher

  def getItems: Source[Item2, NotUsed] = Source.fromIterator(
    () => Iterator.fill(10) {
      var id = Random.nextInt()
      id = if (id < 0) -id else id
      Item2("book", id)
    }
  )

  val route = {
    pathSingleSlash {
      get {
        complete(Item("book", 1)) // marshall (serialize) Item to a JSON String
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
//    } ~
//    path("example2") {
//      //import JsonSupport2._
//      //implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()
//      get {
//        import JsonSupport2._
//        //val item2: Source[Item2, NotUsed] = Source.single(Item2("book", 2))
//        val items: Source[Item2, NotUsed] = getItems
//        complete(items)
//        // ex. curl http://localhost:9000/example2
//        // this returns:
//        //   {name: "book", id: 2}
//      } ~
//      post {
//        entity(as[Order2]) { order => // un-marshall (deserialize) JSON String to Order class
//          val count = order.items.size
//          val names = order.items.map(_.name).mkString(", ")
//          complete(s"$count items: $names")
//          // ex. curl -X POST -d '{"items":[{"name":"book1","id":1}, {"name":"book2","id":2}]}' \
//          //       -H "Content-Type: application/json" "http://localhost:9000/example2"
//        }
//      }
    } ~
    pathPrefix("example3") {
      get {
        complete(Item3("book", 3))
        // ex. curl http://localhost:9000/example3
        // this returns:
        //   {name: "book", id: 3}
      } ~
      post {
        entity(as[Order3]) { order => // un-marshall (deserialize) JSON String to Order class
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
