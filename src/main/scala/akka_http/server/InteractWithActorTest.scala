package akka_http.server

import akka.actor.{Actor, ActorSystem, Props, ActorLogging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json.DefaultJsonProtocol._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn


// Akka HTTP routes easily interacts with actors:
// ex.
//   one route allows for placing bids in a fire-and-forget style
//   another route contains a request-response interaction with an actor
// the resulting response is rendered as json and returned when the response arrives from the actor
object InteractWithActorTest {
  case class Bid(userId: String, offer: Int)
  case object GetBids
  case class Bids(bids: List[Bid])

  class Auction extends Actor with ActorLogging {
    var bids = List.empty[Bid]
    def receive = {
      case bid @ Bid(userId, offer) =>
        bids = bids :+ bid
        log.info(s"Bid complete: $userId, $offer")
      case GetBids => sender() ! Bids(bids)
      case _ => log.info("Invalid message")
    }
  }

  // these are from spray-json
  implicit val bidFormat = jsonFormat2(Bid)
  implicit val bidsFormat = jsonFormat1(Bids)

  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val auction = system.actorOf(Props[Auction], "auction")

    val route =
      path("auction") {
        get {
          parameter("bid".as[Int], "user".as[String]) { (bid, user) =>
            auction ! Bid(user, bid) // tell actor: place a bid, fire-and-forget
            complete((StatusCodes.Accepted, "bid placed"))
          }
        } ~
          get {
            implicit val timeout: Timeout = 5.seconds

            val bids: Future[Bids] = (auction ? GetBids).mapTo[Bids] // ask actor: query actor for the current auction state
            complete(bids)
          }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println(s"Server running at http://localhost:8080/\n" +
      s"http://localhost:8080/auction?user=nobody&bid=5000\n" +
      s"http://localhost:8080/auction\n" +
      s"Press RETURN to stop..."
    )
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done

  }
}
