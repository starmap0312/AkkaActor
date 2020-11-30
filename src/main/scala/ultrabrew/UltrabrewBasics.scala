package ultrabrew

import io.ultrabrew.metrics.MetricRegistry
import io.ultrabrew.metrics.reporters.SLF4JReporter
import org.slf4j.{Logger, LoggerFactory}

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
  val sizeGauge = metricRegistry.gauge("size") // Gauge measures a 64-bit integer value at given time

  def countError(tagList: Array[String]): Unit = {
    errorCounter.inc(tagList: _*)
  }

  def setSize(value: Long, tagList: Array[String]): Unit = {
    sizeGauge.set(value, tagList: _*)
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
  test.setSize(1, Array("size-tag1", "v3"))
  test.setSize(2, Array("size-tag1", "v3"))
  test.setSize(1, Array("size-tag1", "v3"))
  // 2020-11-30 18:03 INFO  metrics - lastUpdated=1606730603699 size-tag1=v3 count=3 sum=4 min=1 max=2 lastValue=1 size

  Thread.sleep(5000)

  test.setSize(1, Array("size-tag1", "v3"))
  test.setSize(2, Array("size-tag1", "v3"))
  // 2020-11-30 18:07 INFO  metrics - lastUpdated=1606730820017 size-tag1=v3 count=2 sum=3 min=1 max=2 lastValue=2 size
  
  Thread.sleep(5000)
}
