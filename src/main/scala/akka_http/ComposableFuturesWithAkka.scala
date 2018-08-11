package akka_http

// https://www.youtube.com/watch?v=VCattsfHR4o
// 1) Threads
//    independent, heap-sharing execution context, scheduled by operating system
//    thread creation is expensive in Java
//    pools create additional complexity
//
object ComposableFuturesWithAkka extends App {
  println("hi")
}
