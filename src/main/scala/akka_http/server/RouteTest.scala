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
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller, ToResponseMarshallable}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.Failure

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

    val route: Route = {
      get {
        path("path") { // only /path is handled, but not /path/
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello World</h1>")) // Hello World
        } ~
        pathPrefix("pathPrefix") { // /pathPrefix, /pathPrefix/, or /pathPrefix/1 are handled
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello World</h1>")) // Hello World
        } ~
        pathPrefix("mechanic") { // http://localhost:8080/mechanic/refeed/abc?p=0&q=1
          pathPrefix("refeed") {
            ctx =>
              val subpath = ctx.unmatchedPath.toString().drop(1)                    // abc
              val q: String = ctx.unmatchedPath.toString().drop(1) + ctx.request.uri.rawQueryString.map("?" + _).getOrElse("")
              val params = ctx.request.uri.rawQueryString.map("?" + _).getOrElse("")// p=0&q=1 ---> ?p=0&q=1
              ctx.complete(subpath + ", " + params)                                 // abc, ?p=0&q=1
          }
        } ~
        path("path1" / "path2") { // only /path1/path2 is handled, but not /path1/path2/
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Hello World</h1>")) // Hello World
        } ~
        path("path3" / Segment) { segment => // /path3/[segment] are handled, but not /path3/segment/
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>$segment</h1>")) // Hello World
        } ~
        path("path4" / Segment.?) { someSegment => // /path4/ or /path4/[segment] are handled, but not /path4 and not /path4/segment/
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>${someSegment.getOrElse("no segment")}</h1>")) // Hello World
        } ~
        (path("path5" / Segment.?) & parameterMap) { (someSegment: Option[String], params: Map[String, String]) => // /path5/?a=1&b=2 or /path5/[segment]?a=1&b=2 are handled, but not /path4 and not /path4/segment/
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>${someSegment.getOrElse("no segment")}, ${params}</h1>")) // Hello World
        } ~
        path("future") { // ex. ask an actor for a value
          complete(Future("a future value")) // a future value
        } ~
        path("exception") { // ex. ask an actor for a value
          complete(
            Future.failed(new Exception("recover from a Future failed new Exception")) recover {
              case ex: Throwable => ex.getMessage
            }
          )
        } ~
        path("readTree") { // ex. objectMapper.readTree([jsonString])
          // de-serialize Json String as JsonNode
          val jsonnode = mapper.readTree("""{"name": "john", "age": 10}""")
          // serialize JsonNoe  as Json String
          complete(jsonnode.toString) // {name: "john", age: 10}
        }  ~
        path("readValue") { // ex. de-serialize: objectMapper.readValue[Map[String, Any]]([jsonString])
          // de-serialize Json String  as Map[String, Any]
          val map = mapper.readValue[Map[String, Any]]("""{"name": "john", "age": 10}""")
          // serialize Map[String, Any]  as Json String
          complete(mapper.writeValueAsString(map)) // {name: "john", age: 10}
        } ~
        path("writeValueAsString") { // ex. objectMapper.writeValueAsString([map])
          complete(mapper.writeValueAsString(Map("name" -> "john", "age" -> 10))) // {name: "john", age: 1}
          // this returns with header: Content-Type: text/plain; charset=UTF-8
        } ~
        path("writeValueAsBytes") { // ex. objectMapper.writeValueAsString([map])
          // serialize Map[String, Any] as byte[]
          complete(mapper.writeValueAsBytes(Map("name" -> "john", "age" -> 10))) // {name: "john", age: 1}
          // this returns with header: Content-Type: application/octet-stream
        } ~
        path("map") { // ex. define implicit val ToEntityMarshaller
          // serialize Map[String, Any] as byte[]
          implicit val marshaller: ToEntityMarshaller[Any] = { // ToEntityMarshaller[Any] == Marshaller[Any, MessageEntity]
            Marshaller.withFixedContentType(MediaTypes.`application/json`) {
              anyObject => HttpEntity(MediaTypes.`application/json`, mapper.writeValueAsString(anyObject))
            }
          }
          complete(Map("name" -> "john", "age" -> 10)) // {name: "john", age: 1}
          // note that:
          //   complete(m: => ToResponseMarshallable) does not take a Map, so we need to define an implicit marshaller in the scope
          //   ex. we could define the implicit marshaller in a trait & extends the trait
          // this returns with header: Content-Type: application/json !!!!
        } ~
        path("httpEntity") { // ex. define implicit val ToEntityMarshaller
          val e: UniversalEntity = HttpEntity(MediaTypes.`application/json`, mapper.writeValueAsString(Map("name" -> "john", "age" -> 10)))
          complete(
            HttpEntity(MediaTypes.`application/json`, mapper.writeValueAsString(Map("name" -> "john", "age" -> 10)))
          ) // {name: "john", age: 1}
          // this returns with header: Content-Type: application/json !!!!
        } ~
        path("params") {
          parameters("x") { x => // x OR y is required; otherwise, you get message "Request is missing required query parameter 'x'"
            complete(x)
          } ~ parameter("y") { y =>
            complete(y)
          }
        }
      }
    }
    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(s"Server at http://localhost:8080/path\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return

    bindingFuture
      .flatMap(_.unbind())                 // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
