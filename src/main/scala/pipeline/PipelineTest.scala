package pipeline

import java.text.SimpleDateFormat

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

case class Context(parameters: Map[String, Any] = Map(), results: Results = Results(), errors: Seq[ErrorEntry] = Seq(), logs: Seq[LogEntry] = Seq(), requests: Seq[Request] = Seq(), responses: Map[Request, Response] = Map()) {

  // utility method to access a parameter
  def get[T](name: String)(implicit ctag: ClassTag[T]): T = {
    ctag.unapply(parameters(name)).get
  }

  // these help to mutate logs & errors of context safely and easily, with visibility
  lazy val parameterBuilder = Array.newBuilder[(String, Any)]
  lazy val logBuilder = Seq.newBuilder[LogEntry]
  lazy val errorBuilder = Seq.newBuilder[ErrorEntry]
}

object LogEntry {
  val df = new SimpleDateFormat("mm/dd HH:mm")
}
case class LogEntry(message: String, time: Long = System.currentTimeMillis()) {
  override def toString: String = s"[${LogEntry.df.format(time)}] $message"
}

object ErrorEntry {
  val FATAL = "fatal"
  val RECOVERABLE = "recoverable"
}
case class ErrorEntry(message: String, level: String = ErrorEntry.RECOVERABLE)

case class Results(status: Int = 500, results: Map[String, String] = Map(), meta: Map[String, Any] = Map())


class Pipeline(tasks: Vector[Task]) extends Actor with ActorLogging {
  import context.dispatcher
  var stage: Int = 0           // current stage: a mutable state of the actor

  var receiver: ActorRef = null

  def receive = {
    case ctx: Context if stage >= tasks.length => // reach the end of the pipeline
      complete(ctx.copy(logs = ctx.logs :+ LogEntry("pipeline completes"))) // append one more log to the context
    case ctx: Context =>
      if (stage == 0) receiver = sender() // initialize the receiver at stage 0
      val task = tasks(stage)
      stage += 1
      val taskExecution = task.execute(ctx, context) // asynchronously execute the task
      taskExecution.onComplete {
        case Success(ctx) =>   // if the task future succeeds
          if (ctx.errors.filter(_.level == ErrorEntry.FATAL).nonEmpty) {
            complete(ctx)      // abort the pipeline in the case of fatal errors
          } else {
            receive(ctx)       // otherwise, proceed to the next stage of the pipeline
          }
        case Failure(ex) =>    // if the task future fails
          task.fatal(ex)(ctx)
          complete(task.result(ctx))
      }
    case 'cancel =>
    case msg => log.error("Unhandled message {}", msg)
  }

  def complete(ctx: Context): Unit = {
    receiver ! ctx
    timer.cancel()
    context.stop(self)
  }
  val timer = context.system.scheduler.scheduleOnce(500.millis, self, 'cancel)
}

trait Task {
  def name: String

  def execute(implicit ctx: Context, actorContext: ActorContext): Future[Context] = { // this can be overridden to a non-blocking Future of Context
    log(s"executes task $name") // do nothing: blocking on Context
    Future.successful(result(ctx))
  }

  def result(ctx: Context): Context = {
    ctx.copy(
      parameters = ctx.parameterBuilder.++=(ctx.parameters).result().reverse.toMap,
      results = ctx.results,
      logs = ctx.logs ++ ctx.logBuilder.result(),
      errors = ctx.errors ++ ctx.errorBuilder.result(),
    )
  }

  /**
    * Utility method to set a parameter value.
    */
  def parameter(name: String, value: Any)(implicit ctx: Context): Unit = {
    log(s"set parameter '$name' -> '$value'")
    ctx.parameterBuilder += (name -> value)
  }

  def log(msg: => String)(implicit ctx: Context): Unit = {
    ctx.logBuilder += LogEntry(msg)
  }

  def error(msg: String)(implicit ctx: Context): Unit = {
    ctx.errorBuilder += ErrorEntry(msg, level = ErrorEntry.RECOVERABLE)
  }

  def fatal(ex: Throwable)(implicit ctx: Context): Unit = {
    ctx.errorBuilder += ErrorEntry(s"Fatal error: (${ex.getMessage})", level = ErrorEntry.FATAL)
  }
}
class LocalTask1(val name: String) extends Task {
  override def execute(implicit ctx: Context, actorContext: ActorContext): Future[Context] = { // this can be overridden to a non-blocking Future of Context
    log(s"executes task $name") // do nothing: blocking on Context
    parameter("param1", 1)
    parameter("param2", 2)
    error("a non-fatal error occurs")
    Future.successful(result(ctx))
  }
}
class LocalTask2(val name: String) extends Task {
  override def execute(implicit ctx: Context, actorContext: ActorContext): Future[Context] = { // this can be overridden to a non-blocking Future of Context
    log(s"executes task $name") // do nothing: blocking on Context
    parameter("param1", 3)
    parameter("param2", 4)
    Future.successful(result(ctx))
  }
}
class HttpTask(val name: String) extends Task {
  override def execute(implicit ctx: Context, actorContext: ActorContext): Future[Context] = {
    log(s"add remote task $name")
    val reqs = Seq(Request(uri = "http://www.example.com", timeout = 500.millis))
    Future.successful(result(ctx).copy(requests = reqs))
  }
}
class HttpExecution(val name: String) extends Task {
  // import akka.pattern.ask // import if need to ask another actor
  override def execute(implicit ctx: Context, actorContext: ActorContext): Future[Context] = {
    log(s"execute remote task $name")
    import actorContext.dispatcher
    import actorContext.system
    val responsesFuture: Seq[Future[(Request, Response)]] = ctx.requests.map { req: Request =>
      // implicit val timeout = akka.util.Timeout(req.timeout) // specify timeout of asking another actor: will receive Failure(AskTimeoutException)
      Http().singleRequest(HttpRequest(uri = req.uri)).map { resp: HttpResponse =>
        req -> Response(resp)
      }.recover {
        case ex: Throwable => req -> Response(ex, hasError = true) // ex. in the case of timeout, set the response to hasError
      }
    }
    val future: Future[Context] = Future.sequence(responsesFuture).map { responses: Seq[(Request, Response)] =>
      responses.foreach {
        case (req, Response(ex: Throwable, hasError)) if hasError => error(s"Request fails")
        case (req, _: Any) =>
      }
      result(ctx).copy(requests = Seq(), responses = ctx.responses ++ responses) // empty the requests and append the responses to the context
    }
    future
  }
}
class HttpPostExecution(val name: String) extends Task {
  override def execute(implicit ctx: Context, actorContext: ActorContext): Future[Context] = {
    log(s"execute post task $name")
    ctx.responses.filter(_._1.uri.nonEmpty).foreach {
      case (req, Response(resp: HttpResponse, _)) => parameter("status", resp.status) // set context parameter: status based on remote task response
      case (req, _: Any) =>
    }
    Future.successful(result(ctx))
  }
}


case class Request(uri: String, timeout: FiniteDuration = 50.millis)
case class Response(@transient response: Any, hasError: Boolean = false)

object PipelineText extends App {
  implicit val system = ActorSystem("PipelineText")
  implicit val timeout = Timeout(3.seconds)
  import system.dispatcher

  val pipeline = system.actorOf(
    Props(
      new Pipeline(Vector(
        new LocalTask1("local task1"),
        new LocalTask2("local task2"),
        new HttpTask("http request"),
        new HttpExecution("http execution"),
        new HttpPostExecution("http post execution")
      ))
    ),
    "pipeline"
  )

  val result: Future[Context] = (pipeline ? Context()).mapTo[Context] // returns a Future which may be a Failure(AskTimeoutException)
  result onComplete {
    case Success(ctx) =>
      ctx.logs.foreach(println)
//      [38/03 17:38] executes task local task1
//      [38/03 17:38] set parameter 'param1' -> '1'
//      [38/03 17:38] set parameter 'param2' -> '2'
//      [38/03 17:38] executes task local task2
//      [38/03 17:38] set parameter 'param1' -> '3'
//      [38/03 17:38] set parameter 'param2' -> '4'
//      [38/03 17:38] add remote task http request
//      [38/03 17:38] execute remote task http execution
//      [06/04 11:06] execute post task http post execution
//      [38/03 17:38] pipeline completes
      ctx.parameters.foreach(println)
//      (param1,3)
//      (param2,4)
//      (status,200 OK)
      ctx.errors.foreach(println)
//      ErrorEntry(a non-fatal error occurs,recoverable)
    case Failure(ex) => println(ex.getMessage) // AskTimeoutException: the recipient actor didn't send a reply within timeout
  }
  StdIn.readLine()
  system.terminate()

}
