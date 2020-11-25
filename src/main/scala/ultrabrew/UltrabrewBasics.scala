package ultrabrew

import io.ultrabrew.metrics.MetricRegistry
import io.ultrabrew.metrics.reporters.SLF4JReporter
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class TestResource(metricRegistry: MetricRegistry) {

  val errorCounter = metricRegistry.counter("error") // Counter increment or decrement a 64-bit integer value
  val sizeGauge = metricRegistry.gauge("size") // Gauge measures a 64-bit integer value at given time

  def handleError(value1: String, value2: String): Unit = {
    errorCounter.inc("tag1", value1, "tag2", value1)
    // .. do something ..
  }

  def handleSize(value: Long, tagList: Array[String]): Unit = {
    sizeGauge.set(value, tagList: _*)
    // .. do something ..
  }
}

object UltrabrewBasics extends App {

  val logger = LoggerFactory.getLogger(this.getClass)

  // MetricRegistry
  //   it holds the configurations for metrics including metric name and type
  //   it also tracks and reports the metrics
  val metricRegistry: MetricRegistry = new MetricRegistry
  // SLF4JReporter: reports to SLF4J Logger with given name to log the aggregated values of the metrics
  val reporter: SLF4JReporter = SLF4JReporter.builder().withName("metrics").build()

  metricRegistry.addReporter(reporter)

  val test = new TestResource(metricRegistry)

  test.handleError("v1", "v2")
  test.handleError("v1", "v2")

  test.handleSize(1, Array("tag3", "v3"))
  test.handleSize(2, Array("tag3", "v3"))

  logger.debug("hello")
}
