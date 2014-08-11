package cjmx.util

import java.io.{BufferedReader, InputStream, IOException}
import java.util.concurrent.BlockingQueue

import scalaz._
import Scalaz._
import scalaz.concurrent.Task
import scalaz.effect._
import scalaz.stream.{Cause, Process}

object MoreEnumerators {

  /** Return the stream of lines from the given `BufferedReader`. */
  def linesR(src: BufferedReader): Process[Task,String] =
    Process.repeatEval { readLine(src) }
           .onComplete { Process.eval_(Task.delay(src.close)) }

  private def readLine(src: BufferedReader): Task[String] =
    Task.delay {
      val line = src.readLine
      if (line eq null) throw Cause.Terminated(Cause.End) // null signals EOF
      else line
    }

  /**
   * Returns the stream of elements from the given `BlockingQueue`,
   * using `Done` to terminate the listener. The `termination`
   * side effect is run when the stream is completed.
   */
  def enumBlockingQueue[A](q: BlockingQueue[Signal[A]], termination: => Any = ()): Process[Task,A] =
    Process.repeatEval(Task.delay {
      q.take match {
        case Done => throw Cause.Terminated(Cause.End)
        case Value(a) => a
      }
    }) onComplete (Process.eval_(Task.delay(termination)))

  /**
   * Message type for `BlockingQueue`-based stream. Enqueueing a `Done`
   * terminates the stream.
   */
  sealed trait Signal[+A]
  final case class Value[A](value: A) extends Signal[A]
  final case object Done extends Signal[Nothing]
}
