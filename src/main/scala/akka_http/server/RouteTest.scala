package akka_http.server

// akka modules
// 1) akka-http-core:
//    a complete, low-level, server- and client-side implementation of HTTP (including WebSockets)
// 2) akka-http: used to write HTTP servers
//    it provides higher-level functionality, like (un)marshalling, (de)compression
//    it provides a powerful DSL for defining HTTP-based APIs on the server-side
// 3) akka-http-spray-json:
//    predefined glue-code for (de)serializing custom types from/to JSON with spray-json
// 4) akka-http-xml:
//    predefined glue-code for (de)serializing custom types from/to XML with scala-xml

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.concurrent.Future
import scala.io.StdIn

// Set up a simple web-server that responds: <h>Hello World</h1>
object RouteTest {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("my-webserver")
    implicit val materializer = ActorMaterializer()   // needed for the future flatMap/onComplete in the end (i.e. val bindingFuture)
    implicit val executionContext = system.dispatcher

    val mapper = new ObjectMapper with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    mapper.setSerializationInclusion(Include.NON_ABSENT)

    val route = {
      get {
        path("path") { // only /path is handled, not /path/
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello World</h1>")) // Hello World
        } ~
        pathPrefix("pathPrefix") { // /pathPrefix, /pathPrefix/, or /pathPrefix/1 are handled
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello World</h1>")) // Hello World
        } ~
        path("path1" / "path2") { // only /path1/path2/ is handled
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello World</h1>")) // Hello World
        } ~
        path("future") { // ex. ask an actor for a value
          complete(Future("a future value")) // a future value
        } ~
        path("readTree") { // ex. objectMapper.readTree([jsonString]).toString
          complete(mapper.readTree("""{"name": "john", "age": 10}""").toString) // {name: "john", age: 10}
        }  ~
        path("writeValueAsString") { // ex. objectMapper.writeValueAsString([map])
          complete(mapper.writeValueAsString(Map("name" -> "john", "age" -> 10))) // {name: "john", age: 1}
        }
      }
    }
    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server at http://localhost:8080/path\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return

    bindingFuture
      .flatMap(_.unbind())                 // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
