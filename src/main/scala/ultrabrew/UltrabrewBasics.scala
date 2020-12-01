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
  //  it is stored in a 64-bit long, and it is reported as:
  //  - Sum (e.g., total latency of request processing over default=60.seconds)
  //  - Count (e.g., number of requests processed over default=60.seconds)
  //  - Min (e.g., lowest latency across all requests over default=60.seconds)
  //  - Max (e.g., highest latency across all requests over default=60.seconds)
  val latencyGauge = metricRegistry.gauge("latency") // Gauge measures a 64-bit integer value at given time

  // Timer: measure elapsed time between two events and act as counter for these events
  val requestTimer = metricRegistry.timer("requests")

  def countError(tagList: Array[String]): Unit = {
    errorCounter.inc(tagList: _*)
  }

  def setSize(value: Long, tagList: Array[String]): Unit = {
    latencyGauge.set(value, tagList: _*)
  }

  def handleRequest(tagList: Array[String]): Unit = {
    val startTime = requestTimer.start()
    // .. handle request ..
    Thread.sleep((Math.random() * 100).toLong)
    // Note: no need for separate counter for requests per sec, as count is already included
    requestTimer.stop(startTime, tagList: _*)
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

  test.countError(Array("error-tag1", "v1", "error-tag1", "v2"))
  // 2020-11-30 18:03 INFO  metrics - lastUpdated=1606730603689 error-tag1=v1 error-tag1=v2 sum=1 error
  test.countError(Array("error-tag1", "v1", "error-tag2", "v2"))
  // 2020-11-30 18:03 INFO  metrics - lastUpdated=1606730603696 error-tag1=v1 error-tag2=v2 sum=1 error
  test.setSize(1, Array("latency-tag1", "v3"))
  test.setSize(2, Array("latency-tag1", "v3"))
  test.setSize(1, Array("latency-tag1", "v3"))
  // 2020-11-30 18:03 INFO  metrics - lastUpdated=1606730603699 latency-tag1=v3 count=3 sum=4 min=1 max=2 lastValue=1 latency

  Thread.sleep(3000)

  test.setSize(1, Array("latency-tag1", "v3"))
  test.setSize(2, Array("latency-tag1", "v3"))
  // 2020-11-30 18:07 INFO  metrics - lastUpdated=1606730820017 latency-tag1=v3 count=2 sum=3 min=1 max=2 lastValue=2 latency

  Thread.sleep(3000)

  test.handleRequest(Array("status", String.valueOf(200)))
  test.handleRequest(Array("status", String.valueOf(400)))
  test.handleRequest(Array("status", String.valueOf(500)))
  test.handleRequest(Array("status", String.valueOf(200)))
  // 2020-12-01 10:57 INFO  metrics - lastUpdated=1606791441577 status=200 count=2 sum=153287179 min=75395218 max=77891961 requests
  // 2020-12-01 10:57 INFO  metrics - lastUpdated=1606791441428 status=400 count=1 sum=96555162 min=96555162 max=96555162 requests
  // 2020-12-01 10:57 INFO  metrics - lastUpdated=1606791441502 status=500 count=1 sum=73397428 min=73397428 max=73397428 requests

  Thread.sleep(3000)
}
