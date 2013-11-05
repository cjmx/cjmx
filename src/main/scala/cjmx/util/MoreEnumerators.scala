package cjmx.util

import java.io.{BufferedReader, InputStream, IOException}
import java.util.concurrent.BlockingQueue

import scalaz._
import Scalaz._
import scalaz.concurrent.Task
import scalaz.effect._
import scalaz.stream.{async, Process}

object MoreEnumerators {

  /** Return the stream of lines from the given `BufferedReader`. */
  def linesR(src: BufferedReader): Process[Task,String] =
    Process.repeatEval { readLine(src) }
           .onComplete { Process.eval_(Task.delay(src.close)) }

  /** Terminate `s` when it first returns `None`. */
  def untilNone[A](s: Process[Task, Option[A]]): Process[Task,A] =
    s.takeWhile(_.nonEmpty).map(_.get)

  private def readLine(src: BufferedReader): Task[String] =
    Task.delay {
      val line = src.readLine
      if (line eq null) throw Process.End // null signals EOF
      else line
    }
}
