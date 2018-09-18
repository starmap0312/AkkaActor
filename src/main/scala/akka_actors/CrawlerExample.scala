package akka_actors

//import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout, Status}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
//import org.jsoup.Jsoup

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

// 1) a reactive application is non-blocking & event-driven, top to bottom
// 2) actors are run by a dispatcher (which is shared and can also be used to run Future)
// 3) blocking inside an Actor is not recommended, as it wastes one thread (a finite resource)

object CrawlerExample extends App {
  val system = ActorSystem("CrawlerExample")

  object Getter {
    case object Abort
    case object Done
  }
  class Getter(url: String, depth: Int) extends Actor with ActorLogging {
    import Getter._
    implicit val akkaSystem = system                // we need ActorSystem for akka Http.apply() method
    implicit val dispacher = context.dispatcher     // we need ExecutionContext for future onComplete() method
    implicit val materializer = ActorMaterializer() // we need Materializer for akka Entity toStrict() method

    val futureBody = get(url)
    futureBody onComplete {
      case Success(body) => self ! body
      case Failure(ex) => self ! Status.Failure(ex)
    }
    //futureBody pipeTo self

    override def receive: Receive = {
      case body: String => log.debug(body)
      case _: Status.Failure => stop
      case Abort => stop
    }

    def stop(): Unit = {
      context.parent ! Getter.Done
      context.stop(self)
    }

    def get(url: String) = {
      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = url))
      responseFuture flatMap {
        response => {
          if (response.status.intValue() < 400) {
            response.entity.toStrict(3 seconds).map(_.data.decodeString("UTF-8"))
            // ResponseEntity.toStrict([timeout]):
            //   consume the entire entity as Strict entity (i.e. completely loaded into memory)
            //   used to eagerly consume the entity and make it available in memory:
          } else {
            throw new RuntimeException("Bad status")
          }
        }
      }
    }
  }


  object Controller {
    case class Check(url: String, depth: Int)
    case class Result(cache: Set[String])
    case object Timeout
  }
  class Controller extends Actor with ActorLogging {
    import Controller._
    implicit val dispacher = context.dispatcher     // we need ExecutionContext for scheduleOnce() method

    // we prefer immutable Set since they can be shared
    var cache: Set[String] = Set.empty[String] // use variable Set pointing to immutable Set, so that when parent receive
                                               // the resulting cache, it can modify the Set without affecting the Controller
    var children: Set[ActorRef] = Set.empty[ActorRef]

    context.setReceiveTimeout(10.seconds) // the receive timeout is reset by after processing every received message
                                          // when it expires, the Actor will receive a ReceiveTimeout message

    context.system.scheduler.scheduleOnce(10.seconds, self, Timeout) // add an overall Timeout to abort all Getter tasks

    override def receive: Receive = {
      case Check(url, depth) =>
        log.debug("{} checking {}", depth, url)
        if (!cache(url) && depth > 0) {
          children += context.actorOf(Props(new Getter(url, depth - 1)))
        }
        cache += url
      case Getter.Done =>
        children -= sender
        if (children.isEmpty) { // when no Getter is running, return the result URLs to the parent Actor
          context.parent ! Result(cache)
        }
      case ReceiveTimeout =>
        children.foreach(_ ! Getter.Abort)
      case Timeout =>
        children.foreach(_ ! Getter.Abort)
    }
  }

  object Receptionist {
    case class Job(cleint: ActorRef, url: String)
    case class Get(url: String)
    case class Failed(url: String)
    case class Result(url: String, links: Set[String])
  }
  class Receptionist extends Actor with ActorLogging {
    import Receptionist._
    override def receive: Receive = waiting

    val waiting: Receive = {
      // upon receiving Get(url), start a traversal and become running
      case Get(url) =>
        context.become(runNext(Vector(Job(sender, url))))
    }

    def running(queue: Vector[Job]): Receive = {
      // upon receiving Get(url), append the task to the queue and keep running
      // upon receiving Controller.Result(links) ship that to client and run next job of the queue
      case Controller.Result(links) =>
        log.debug("receive Result(links): {}", links.toVector.sorted.mkString("\n"))
        val job = queue.head
        job.cleint ! Result(job.url, links)
        context.stop(sender) // stop the controller, as it is done with its job (we will use a new one for next job)
        context.become(runNext(queue.tail))
      case Get(url) =>
        context.become(enqueueJob(queue, Job(sender, url)))
    }

    var reqNo = 0
    def runNext(queue: Vector[Job]): Receive = {
      reqNo += 1 // as every actor must have a unique name, we use a variable reqNo as its ID
      if (queue.isEmpty) waiting
      else {
        val controller = context.actorOf(Props[Controller], s"controller${reqNo}")
        controller ! Controller.Check(queue.head.url, 2)
        running(queue)
      }
    }

    def enqueueJob(queue: Vector[Job], job: Job): Receive = {
      if (queue.size > 3) {
        sender ! Failed(job.url)
        running(queue)
      } else {
        running(queue :+ job)
      }
    }
  }
  // do not refer to actor state from code running asyncronously, ex. scheduler or future combinators
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
  class Client extends Actor with ActorLogging {
    import Receptionist._
    val receptionist = system.actorOf(Props[Receptionist], "receptionist")

    receptionist ! Receptionist.Get("http://www.google.com")

    context.setReceiveTimeout(10.seconds)

    override def receive: Receive = {
      case Result(url, links) =>
        log.debug("Client receives Result({})", links.toVector.sorted.mkString("\n"))
      case Failed(url) =>
        log.debug("Fail({})", url)
      case ReceiveTimeout =>
        context.stop(self)
    }
  }
  val client = system.actorOf(Props[Client], "client")
  //system.terminate()
}
