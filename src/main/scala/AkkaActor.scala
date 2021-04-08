import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

object Akkaactor extends App {
  val logger = LoggerFactory.getLogger(getClass.getName)
  logger.debug("Debug message")
  logger.info("Default executable for docker image") // [main] INFO Akkaactor$ - Default executable for docker image

  // access system environment & properties
  val envConfig = ConfigFactory.systemEnvironment().resolve()

  println(s"envConfig ${envConfig.root().keySet()}") // envConfig [PATH, JAVA_HOME, JAVA_OPTS, TERM, LOGNAME, PWD, TERM_PROGRAM_VERSION, XPC_SERVICE_NAME, __CFBundleIdentifier, NVM_CD_FLAGS, SHELL, NVM_DIR, TERM_PROGRAM, SBT_OPTS, USER, TMPDIR, SSH_AUTH_SOCK, XPC_FLAGS, TERM_SESSION_ID, __CF_USER_TEXT_ENCODING, NVM_BIN, JAVA_MAIN_CLASS_37478, SHLVL, HOME, JAVA_MAIN_CLASS_37411]

  // 1) set property in code:
  System.setProperty("arg1", "value1")

  val defaultConfig = ConfigFactory.defaultOverrides()
  println(s"defaultConf: ${defaultConfig.root().keySet()}") // defaultConf: [jdk, arg1, path, file, java, os, line, mx, user, sun]

  // 1) set property by docker run command line:
  // if you run:
  //   sbt docker:publishLocal
  //   docker run -it --rm cf339085f332 -Darg2=value2
  // it will print out: [jdk, arg1, arg2, path, file, java, os, line, user, sun]

  val appConfig = ConfigFactory.defaultApplication()
  println(s"appConfig: ${appConfig.root().keySet()}") // appConfig: [app, akka]

  val systemConfig = defaultConfig.withFallback(appConfig).resolve()
  println(s"conf: ${systemConfig.root().keySet()}") // default properties: [jdk, path, file, java, os, line, user, sun]


  // note: it is required instantiate an ActorSystem to create the CountExtension
  val system = ActorSystem("akaactor", systemConfig)
  println("Akkaactor is run")

  system.terminate()
}
