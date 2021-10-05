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
    // or use update(startTime, tagList), ex.
    // latencyTimer.update(System.nanoTime() - startTime, tagList: _*)
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
  test.countError(Array("cache-error", "v1", "cache-error", "v2"))
  test.countError(Array("cache-error", "v1", "cache-error", "v2")) // cache-error sum=3 is reported
  // INFO metrics - lastUpdated=1633416531266 cache-error=v1 cache-error=v2 sum=3 error
  Thread.sleep(61000)
  test.countError(Array("cache-error", "v1", "cache-error", "v2"))
  test.countError(Array("cache-error", "v1", "cache-error", "v2"))
  test.countError(Array("cache-error", "v1", "cache-error", "v2"))
  test.countError(Array("cache-error", "v1", "cache-error", "v2")) // cache-error sum=4 is reported
  // INFO metrics - lastUpdated=1633416680446 cache-error=v1 cache-error=v2 sum=4 error

  Thread.sleep(3000)
  test.countError(Array("cache-error", "v1", "cache-error2", "v2")) // cache-error2=v2 sum=1 is reported
  // INFO metrics - lastUpdated=1633416681448 cache-error=v1 cache-error2=v2 sum=1 error
  test.setSize(1, Array("cache-size", "v3"))
  test.setSize(2, Array("cache-size", "v3"))
  test.setSize(1, Array("cache-size", "v3")) // cache-size count=3 is reported
  // INFO metrics - lastUpdated=1633416681453 cache-size=v3 count=3 sum=4 min=1 max=2 lastValue=1 cache-size

  Thread.sleep(3000)

  test.setSize(1, Array("cache-size", "v3"))
  test.setSize(2, Array("cache-size", "v3")) // cache-size count=2 is reported
  // INFO metrics - lastUpdated=1633416684460 cache-size=v3 count=2 sum=3 min=1 max=2 lastValue=2 cache-size

  Thread.sleep(3000)

  test.handleRequest(Array("status", String.valueOf(200), "update-time", "v1"))
  test.handleRequest(Array("status", String.valueOf(400), "update-time", "v1"))
  test.handleRequest(Array("status", String.valueOf(500), "update-time", "v1"))
  test.handleRequest(Array("status", String.valueOf(200), "update-time", "v1")) // status=200, count=2 is reported
  // INFO metrics - lastUpdated=1633416687696 status=200 update-time=v1 count=2 sum=94395706 min=32193648 max=62202058 latency
  // INFO metrics - lastUpdated=1633416687566 status=400 update-time=v1 count=1 sum=66777889 min=66777889 max=66777889 latency
  // INFO metrics - lastUpdated=1633416687634 status=500 update-time=v1 count=1 sum=67865079 min=67865079 max=67865079 latency

  Thread.sleep(3000)
}
