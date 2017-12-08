package cjmx

import scala.util.Try

import sbt.io.Path
import sbt.io.syntax._
import sbt.internal.util.{FullReader, LineReader}
import sbt.internal.util.complete.Parser

import cjmx.cli.REPL

object App {
  private val historyFile = (Path.userHome / ".cjmx.history").asFile

  def run(args: Array[String]): Int = {
    val consoleReader = new FullReader(Some(historyFile), _: Parser[_], true)
    val reader: Parser[_] => LineReader = if (args.isEmpty) {
      consoleReader
    } else {
      val firstArgAsConnect = Try(args.head.toInt).toOption.map { pid => "connect -q " + pid }
      firstArgAsConnect match {
        case None =>
          val r = constReader(args :+ "exit")
          p => r
        case Some(cmd) =>
          if (args.tail.isEmpty) {
            prefixedReader(cmd +: args.tail, consoleReader)
          } else {
            val r = constReader(cmd +: args.tail :+ "exit")
            p => r
          }
      }
    }
    REPL.run(reader, Console.out)
  }

  private def constReader(args: Array[String]): LineReader = {
    val iter = args.iterator
    new LineReader {
      override def readLine(prompt: String, mask: Option[Char]) =
        if (iter.hasNext) Some(iter.next) else None
    }
  }

  private def prefixedReader(first: Array[String], next: Parser[_] => LineReader): Parser[_] => LineReader = {
    val firstReader = constReader(first)
    p => new LineReader {
      override def readLine(prompt: String, mask: Option[Char]) =
        firstReader.readLine(prompt, mask) orElse (next(p).readLine(prompt, mask))
    }
  }
}
