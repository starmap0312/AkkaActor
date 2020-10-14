import akka.{Done, NotUsed, pattern, testkit}
import akka.actor.{ActorRef, ActorSystem, Cancellable}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestProbe
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import scala.collection.immutable
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

// https://doc.akka.io/docs/akka/current/stream/stream-testkit.html
class TestStreamProbe extends AnyFlatSpec with should.Matchers {
  implicit val system = ActorSystem("TestStreamProbe")

  "basic tests" can "be done by connecting to testing Sources/Sinks and Await for the result" in {
    // 1) test a Sink by running it with a Source and Await its result
    val sinkUnderTest: Sink[Int, Future[Int]] = Flow[Int].map(_ * 2).toMat(Sink.fold(0)(_ + _))(Keep.right)
    val future1: Future[Int] = Source(1 to 2).runWith(sinkUnderTest)
    // ((0 + ("1" * 2)) + ("2" * 2)) = 6
    val result1 = Await.result(future1, 3.seconds)
    assert(result1 == 6)

    // 2) test a Source by running it with a Sink and Await its result
    val sourceUnderTest: Source[Int, NotUsed] = Source.repeat(1).map(_ * 2).take(10)
    val future2: Future[Seq[Int]] = sourceUnderTest.runWith(Sink.seq)
    val result2: Seq[Int] = Await.result(future2, 3.seconds)
    assert(result2 == Seq.fill(10)(2))

    // 3) test a Flow by running it with a Source & a Sink and Await its result
    val flowUnderTest: Flow[Int, Int, NotUsed] = Flow[Int].takeWhile(_ < 5)
    val future3: Future[Seq[Int]] = Source(1 to 10).via(flowUnderTest).runWith(Sink.fold(Seq.empty[Int])(_ :+ _))
    val result3: Seq[Int] = Await.result(future3, 3.seconds)
    assert(result3 == (1 to 4))
  }

  "TestKit: A TestProbe" can "expect message from a Stream, which is materialized to a future value" in {
    import system.dispatcher
    import akka.pattern.pipe
    val sourceUnderTest: Source[Seq[Int], NotUsed] = Source(1 to 4).grouped(2) // Seq(1, 2), Seq(3, 4)
    // Source.group([size]): Chunk up this stream into groups of the given size
    val future1: Future[Seq[Seq[Int]]] = sourceUnderTest.runWith(Sink.seq)
    val probe: testkit.TestProbe = TestProbe()
    future1.pipeTo(probe.ref) // pipe the future result to a recipient ActorRef (i.e. the probe's ActorRef)
    probe.expectMsg(3.seconds, Seq(Seq(1, 2), Seq(3, 4))) // instead of Await the future result, the probe expect an message piped from the future
  }

  "TestKit: A TestProbe" can "be a Sink of ActorRef that expects message from a Stream" in {
    case object Tick
    val sourceUnderTest: Source[Tick.type, Cancellable] = Source.tick(0.seconds, 200.millis, Tick) // generate a Tick every 200ms

    val probe = TestProbe()
    val cancellable: Cancellable = sourceUnderTest // connecting the Source to a Sink of TestProbe's ActorRef
      .to(Sink.actorRef(probe.ref, onCompleteMessage = "completed", onFailureMessage = _ => "failed"))
      .run()
    // send "completed" message to probe.ref onComplete; send "failed" message to probe.ref onFailure

    probe.expectMsg(1.second, Tick) // initial Tick
    probe.expectNoMessage(100.millis)
    probe.expectMsg(3.seconds, Tick) // 2nd Tick
    cancellable.cancel()
    probe.expectMsg(3.seconds, "completed")
  }

  "TestKit: Creating a test Source of ActorRef" can "send messages to a Sink under test" in {
    val sinkUnderTest: Sink[Int, Future[String]] = Flow[Int].map(_.toString).toMat(Sink.fold("")(_ + _))(Keep.right)
    val source: Source[Nothing, ActorRef] = Source.actorRef( // create a test Source of ActorRef that receives messages of testing input
      completionMatcher = { case Done => CompletionStrategy.draining },
      failureMatcher = PartialFunction.empty, // Never fail the stream because of a message
      bufferSize = 8,
      overflowStrategy = OverflowStrategy.fail
    )
    val (ref: ActorRef, future: Future[String]) = source.toMat(sinkUnderTest)(Keep.both).run()
    ref ! 1
    ref ! 2
    ref ! 3
    ref ! Done
    val result = Await.result(future, 3.seconds)
    assert(result == "123")
  }

  "Streams TestKit" can "send messages to a Sink under test" in {
    // 1) TestSink: create a Sink that can be materialized as a Probe that expects messages from the Source under test
    val sourceUnderTest: Source[Int, NotUsed] = Source(1 to 4).filter(_ % 2 == 0).map(_ * 2) // (2 * 2), (4 * 2), i.e. 4, 8
    val sink1: Sink[Int, TestSubscriber.Probe[Int]] = TestSink.probe[Int]
    val subscriber1: TestSubscriber.Probe[Int] = sourceUnderTest.runWith(sink1)
    subscriber1.request(2).expectNext(4, 8).expectComplete()
    // request(2): note: No events will be sent by a Publisher until subscriber demands them by the request method

    // 2) TestSource: create a Source that can be materialized as a Probe that sends messages to the Sink under test
    val sinkUnderTest2: Sink[Any, NotUsed] = Sink.cancelled
    val source2: Source[Int, TestPublisher.Probe[Int]] = TestSource.probe[Int]

    val publisher2: TestPublisher.Probe[Int] = source2.toMat(sinkUnderTest2)(Keep.left).run()
    publisher2.expectCancellation()

    // 3) TestSource: create a Source that can be materialized as a Probe that sends messages to the Sink under test
    val sinkUnderTest3: Sink[Int, Future[Int]] = Sink.head[Int]
    val source3: Source[Int, TestPublisher.Probe[Int]] = TestSource.probe[Int]
    val runnable2: RunnableGraph[(TestPublisher.Probe[Int], Future[Int])] = source3.toMat(sinkUnderTest3)(Keep.both)

    val (publisher3: TestPublisher.Probe[Int], future: Future[Int]) = runnable2.run()
    publisher3.sendError(new Exception("boom"))

    val result = Await.result(future.failed, 3.seconds)
    assert(result.getMessage == "boom")

    // 4) Use TestSource & TestSink to test a Flow
    import system.dispatcher
    val flowUnderTest: Flow[Int, Int, NotUsed] = Flow[Int].mapAsyncUnordered(2) { sleep =>
      pattern.after(10.millis * sleep, using = system.scheduler)(Future.successful(sleep))
    }
    val source4: Source[Int, TestPublisher.Probe[Int]] = TestSource.probe[Int]
    val sink4: Sink[Int, TestSubscriber.Probe[Int]] = TestSink.probe[Int]

    val (publisher4: TestPublisher.Probe[Int], subscriber4: TestSubscriber.Probe[Int]) = source4.via(flowUnderTest).toMat(sink4)(Keep.both).run()

    subscriber4.request(n = 3) // note: no events will be triggered until subscriber demands them
    publisher4.sendNext(3)
    publisher4.sendNext(2)
    publisher4.sendNext(1)
    subscriber4.expectNextUnordered(1, 2, 3)

    publisher4.sendError(new Exception("Power surge in the linear subroutine C-47!"))
    val ex = subscriber4.expectError()
    assert(ex.getMessage.contains("C-47"))
  }

}