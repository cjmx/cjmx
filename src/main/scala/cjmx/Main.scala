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
      args.head.parseInt match {
        case Success(pid) if args.tail.isEmpty =>
          prefixedReader(Array("connect " + pid), consoleReader)
        case _ =>
          constReader(args ++ Array("quit")).liftReader[Parser[_]]
      }
    }

    val statusCode = REPL.run(reader, new PrintWriter(Console.out, true))
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

