package ultrabrew

import io.ultrabrew.metrics.MetricRegistry
import io.ultrabrew.metrics.reporters.SLF4JReporter
import org.slf4j.{Logger, LoggerFactory}

// Ref: https://github.com/ultrabrew/metrics

class TestResource(metricRegistry: MetricRegistry) {

  // Counter: a type of metric whose value can increase or decrease by whole numbers
  //   it is stored in a 64-bit long, and it is reported as:
  //   - Sum (e.g., total count of requests processed over a minute)
  val errorCounter = metricRegistry.counter("error") // Counter increment or decrement a 64-bit integer value

  // Gauge: a type of metric whose value can increase or decrease over various measurements
  //  it is stored in a 64-bit long
  val cacheSizeGauge = metricRegistry.gauge("cache-size") // Gauge measures a 64-bit integer value at given time

  // Timer: measure elapsed time between two events, in nanoseconds, and act as counter for these events
  //  - Sum (e.g., total latency of request processing over default=60.seconds)
  //  - Count (e.g., number of requests processed over default=60.seconds)
  //  - Min (e.g., lowest latency across all requests over default=60.seconds)
  //  - Max (e.g., highest latency across all requests over default=60.seconds)
  val latencyTimer = metricRegistry.timer("latency")

  def countError(tagList: Array[String]): Unit = {
    errorCounter.inc(tagList: _*)
  }

  def setSize(value: Long, tagList: Array[String]): Unit = {
    cacheSizeGauge.set(value, tagList: _*)
  }

  def handleRequest(tagList: Array[String]): Unit = {
    val startTime = latencyTimer.start()
    // .. handle request ..
    Thread.sleep((Math.random() * 100).toLong) // random sleep [0, 100] ms
    // Note: no need for separate counter for requests per sec, as count is already included
    latencyTimer.stop(startTime, tagList: _*)
  }
}

object UltrabrewBasics extends App {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  // MetricRegistry
  //   it holds the configurations for metrics including metric name and type
  //   it also tracks and reports the metrics
  val metricRegistry: MetricRegistry = new MetricRegistry

  // withStepSize(): sets the reporting interval (frequency), default=60.seconds
  val reporter: SLF4JReporter = SLF4JReporter.builder().withName("metrics").withStepSize(3).build()
  // SLF4JReporter: reports to SLF4J Logger with given name to log the aggregated values of the metrics

  metricRegistry.addReporter(reporter)

  val test = new TestResource(metricRegistry)

  test.countError(Array("cache-error", "v1", "cache-error", "v2"))
  // 2020-11-30 18:03 INFO  metrics - lastUpdated=1606730603689 cache-error=v1 cache-error=v2 sum=1 error
  test.countError(Array("cache-error", "v1", "cache-error2", "v2"))
  // 2020-11-30 18:03 INFO  metrics - lastUpdated=1606730603696 cache-error=v1 cache-error2=v2 sum=1 error
  test.setSize(1, Array("cache-size", "v3"))
  test.setSize(2, Array("cache-size", "v3"))
  test.setSize(1, Array("cache-size", "v3"))
  // 2020-11-30 18:03 INFO  metrics - lastUpdated=1606730603699 cache-size=v3 count=3 sum=4 min=1 max=2 lastValue=1 cache-size

  Thread.sleep(3000)

  test.setSize(1, Array("cache-size", "v3"))
  test.setSize(2, Array("cache-size", "v3"))
  // 2020-11-30 18:07 INFO  metrics - lastUpdated=1606730820017 cache-size=v3 count=2 sum=3 min=1 max=2 lastValue=2 cache-size

  Thread.sleep(3000)

  test.handleRequest(Array("status", String.valueOf(200), "update-time", "v1"))
  test.handleRequest(Array("status", String.valueOf(400), "update-time", "v1"))
  test.handleRequest(Array("status", String.valueOf(500), "update-time", "v1"))
  test.handleRequest(Array("status", String.valueOf(200), "update-time", "v1"))
  // [metrics-1] INFO metrics - lastUpdated=1623992484316 status=200 update-time=v1 count=2 sum=51812394 min=9207269 max=42605125 latency
  // [metrics-1] INFO metrics - lastUpdated=1623992484255 status=400 update-time=v1 count=1 sum=67725859 min=67725859 max=67725859 latency
  // [metrics-1] INFO metrics - lastUpdated=1623992484307 status=500 update-time=v1 count=1 sum=51874143 min=51874143 max=51874143 latency

  Thread.sleep(3000)
}
