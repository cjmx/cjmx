package cjmx.util

import java.io.{BufferedReader, InputStream, IOException}
import java.util.concurrent.BlockingQueue

import scalaz._
import Scalaz._
import scalaz.concurrent.Task
import scalaz.effect._
import scalaz.stream.Process
import scalaz.stream.io

object MoreEnumerators {

  /** Return the stream of lines from the given `BufferedReader`. */
  def linesR(src: BufferedReader): Process[Task,String] =
    Process.repeatEval { Task.delay(src.readLine) }
           .onComplete { Process.eval_(Task.delay(src.close)) }

  /** Ignore all `IOException`s but reraise all others. */
  def ignoreIOExceptions[A](p: Process[Task,A]): Process[Task,A] =
    p.partialAttempt {
      case e: IOException => Process.halt
    } map { _ fold (e => sys.error("unpossible"), identity) }

  /**
   * Returns the stream of elements from the given `BlockingQueue`,
   * using `Done` to terminate the listener. The `termination`
   * side effect is run when the stream is completed.
   */
  def enumBlockingQueue[A](q: BlockingQueue[Signal[A]], termination: => Any = ()): Process[Task,A] =
    Process.repeatEval(Task.delay(q.take())).flatMap {
      case Done => throw Process.End // early termination
      case Value(a) => Process.emit(a)
    } onComplete (Process.eval_(Task.delay(termination)))

  /**
   * Message type for `BlockingQueue`-based stream. Enqueueing a `Done`
   * terminates the stream.
   */
  sealed trait Signal[+A]
  final case class Value[A](value: A) extends Signal[A]
  final case object Done extends Signal[Nothing]
}
