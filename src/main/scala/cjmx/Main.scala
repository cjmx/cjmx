package cjmx

import scalaz._
import Scalaz._

import java.io.PrintWriter

import sbt.{FullReader, LineReader, Path}
import Path._
import sbt.complete.Parser

import cjmx.cli.REPL

object Main {
  private val historyFile = (userHome / ".cjmx.history").asFile

  def main(args: Array[String]) {
    val consoleReader = new FullReader(Some(historyFile), _: Parser[_])
    val reader: Parser[_] => LineReader = if (args.isEmpty) {
      consoleReader
    } else {
      val firstArgAsConnect = args.head.parseInt.map { pid => "connect -q " + pid }
      firstArgAsConnect match {
        case Success(cmd) if args.tail.isEmpty =>
          prefixedReader(cmd +: args.tail, consoleReader)
        case Success(cmd) =>
          constReader(cmd +: args.tail :+ "exit").liftReader[Parser[_]]
        case _ =>
          constReader(args :+ "exit").liftReader[Parser[_]]
      }
    }

    val statusCode = REPL.run(reader, Console.out)
    System.exit(statusCode)
  }

  private def constReader(args: Array[String]): LineReader = {
    val iter = args.iterator
    new LineReader {
      override def readLine(prompt: String, mask: Option[Char]) =
        if (iter.hasNext) Some(iter.next) else None
    }
  }

  private def prefixedReader(first: Array[String], then: Parser[_] => LineReader): Parser[_] => LineReader = {
    val firstReader = constReader(first)
    p => new LineReader {
      override def readLine(prompt: String, mask: Option[Char]) =
        firstReader.readLine(prompt, mask) orElse (then(p).readLine(prompt, mask))
    }
  }

}

