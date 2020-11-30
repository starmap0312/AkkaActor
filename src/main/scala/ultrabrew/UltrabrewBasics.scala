package ultrabrew

import io.ultrabrew.metrics.MetricRegistry
import io.ultrabrew.metrics.reporters.SLF4JReporter
import org.slf4j.{Logger, LoggerFactory}

class TestResource(metricRegistry: MetricRegistry) {

  val errorCounter = metricRegistry.counter("error") // Counter increment or decrement a 64-bit integer value
  val sizeGauge = metricRegistry.gauge("size") // Gauge measures a 64-bit integer value at given time

  def countError(value1: String, value2: String): Unit = {
    errorCounter.inc("error-tag1", value1, "error-tag2", value2)
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
  // SLF4JReporter: reports to SLF4J Logger with given name to log the aggregated values of the metrics
  // note: need to add log4j2.xml configuration file in order to print the metrics to console
  // withStepSize(): sets the reporting frequency, default=60.seconds
  val reporter: SLF4JReporter = SLF4JReporter.builder().withName("metrics").withStepSize(1).build()

  metricRegistry.addReporter(reporter)

  val test = new TestResource(metricRegistry)

  test.countError("v1", "v2") // metrics - lastUpdated=1606378223312 error-tag1=v1 error-tag2=v2 sum=1 error
  Thread.sleep(1100)
  test.countError("v1", "v2") // metrics - lastUpdated=1606378224322 error-tag1=v1 error-tag2=v2 sum=1 error
  Thread.sleep(1100)
  test.setSize(1, Array("size-tag1", "v3")) // metrics - lastUpdated=1606718786712 size-tag1=v3 count=1 sum=1 min=1 max=1 lastValue=1 size
  Thread.sleep(1100)
  test.setSize(2, Array("size-tag1", "v3")) // metrics - lastUpdated=1606718787720 size-tag1=v3 count=1 sum=2 min=2 max=2 lastValue=2 size
  Thread.sleep(1100)
  test.setSize(1, Array("size-tag1", "v3")) // metrics - lastUpdated=1606718946822 size-tag1=v3 count=1 sum=1 min=1 max=1 lastValue=1 size
  Thread.sleep(1100)
}
