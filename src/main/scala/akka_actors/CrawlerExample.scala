package akka_actors

import akka.Done
import akka.actor.{Actor, ActorSystem, Status}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import org.jsoup.Jsoup

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

// 1) a reactive application is non-blocking & event-driven, top to bottom
// 2) blocking inside an Actor is not recommended, as it wastes one thread (a finite resource)

object CrawlerExample extends App {
/*
  val system = ActorSystem("CrawlerExample")
  //implicit val dispatcher = system.dispatcher // we need ExecutionContext for future onComplete method
  //implicit val materializer = ActorMaterializer()

  class Getter(url: String, depth: Int) extends Actor {
    implicit val dispacher = context.dispatcher

    val futureBody = get(url)
    futureBody onComplete {
      case Success(body) => self ! body
      case Failure(ex) => self ! Status.Failure(ex)
    }
    //futureBody pipeTo self

    override def receive: Receive = {
      case body: String => println(body)
      case _: Status.Failure => stop
    }

    def stop(): Unit = {
      context.parent ! Done
      context.stop(self)
    }
  }

  def get(url: String) = {
    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = url))
    responseFuture flatMap {
      response => {
        if (response.status.intValue() < 400) {
          response.entity.toStrict(3 seconds).map(_.data.decodeString("UTF-8"))
        } else {
          throw new RuntimeException("Bad status")
        }
      }
    }
  }

  /*
  val url = "http://www.google.com/"
  def findLinks(body: String) = {
    val document = Jsoup.parse(body, url)
    val links = document.select("a[href]")
    for {
      link <- links.iterator().asScala
    } yield link.absUrl("href")
  }

  get("http://www.google.com") onComplete {
    println _
  }*/
  system.terminate()
  //System.exit(1)
*/
}
