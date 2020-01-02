import org.slf4j.LoggerFactory

object Akkaactor extends App {
  val logger = LoggerFactory.getLogger(getClass.getName)
  logger.debug("Debug message")
  logger.info("Default executable for docker image") // [main] INFO Akkaactor$ - Default executable for docker image
}
