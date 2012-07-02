package cjmx

import java.io.PrintWriter

import sbt.{FullReader, LineReader, Path}
import Path._
import sbt.complete.Parser

import cjmx.cli.REPL

object Main {
  private val historyFile = (userHome / ".cjmx.history").asFile

  def main(args: Array[String]) {
    val reader = if (args.isEmpty) {
      new FullReader(Some(historyFile), _: Parser[_])
    } else {
      val reader = readerFromArgs(args)
      (_: Parser[_]) => reader
    }

    val statusCode = REPL.run(reader, new PrintWriter(Console.out, true))
    System.exit(statusCode)
  }

  private def readerFromArgs(args: Array[String]): LineReader = {
    val iter = (args ++ Array("quit")).iterator
    new LineReader {
      override def readLine(prompt: String, mask: Option[Char]) =
        if (iter.hasNext) Some(iter.next) else None
    }
  }


}

