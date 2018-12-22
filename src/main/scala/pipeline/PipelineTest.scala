package pipeline

import java.text.SimpleDateFormat

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

case class Context(parameters: Map[String, Any] = Map(), results: Results = Results(), errors: Seq[ErrorEntry] = Seq(), logs: Seq[LogEntry] = Seq()) {

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
  var stage: Int = 0        // a mutable state of the actor

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
            receive(ctx)          // otherwise, proceed to the next stage of the pipeline
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
class Task1(val name: String) extends Task {
  override def execute(implicit ctx: Context, actorContext: ActorContext): Future[Context] = { // this can be overridden to a non-blocking Future of Context
    log(s"executes task $name") // do nothing: blocking on Context
    parameter("param1", 1)
    parameter("param2", 2)
    error("a non-fatal error occurs")
    Future.successful(result(ctx))
  }
}
class Task2(val name: String) extends Task {
  override def execute(implicit ctx: Context, actorContext: ActorContext): Future[Context] = { // this can be overridden to a non-blocking Future of Context
    log(s"executes task $name") // do nothing: blocking on Context
    parameter("param1", 3)
    parameter("param2", 4)
    Future.successful(result(ctx))
  }
}

object PipelineText extends App {
  implicit val system = ActorSystem("PipelineText")
  implicit val timeout = Timeout(3.seconds)
  import system.dispatcher

  val pipeline = system.actorOf(Props(new Pipeline(Vector(new Task1("name1"), new Task2("name2")))), "pipeline")

  val result: Future[Context] = (pipeline ? Context()).mapTo[Context] // returns a Future which may be a Failure(AskTimeoutException)
  result onComplete {
    case Success(ctx) =>
      ctx.logs.foreach(println)
//      [30/22 15:30] executes task name1
//      [30/22 15:30] set parameter 'param1' -> '1'
//      [30/22 15:30] set parameter 'param2' -> '2'
//      [30/22 15:30] executes task name2
//      [30/22 15:30] set parameter 'param1' -> '3'
//      [30/22 15:30] set parameter 'param2' -> '4'
//      [30/22 15:30] pipeline completes
      ctx.parameters.foreach(println)
//      (param1,3)
//      (param2,4)
      ctx.errors.foreach(println)
//      ErrorEntry(a non-fatal error occurs,recoverable)
    case Failure(ex) => println(ex.getMessage) // AskTimeoutException: the recipient actor didn't send a reply within timeout
  }
  StdIn.readLine()
  system.terminate()

}