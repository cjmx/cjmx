package cjmx

import scalaz._
import Scalaz._

import java.io.PrintWriter

import sbt.{FullReader, LineReader, Path}
import Path._
import sbt.complete.Parser

import cjmx.cli.REPL

object App {
  private val historyFile = (userHome / ".cjmx.history").asFile

  def run(args: Array[String]): Int = {
    val consoleReader = new FullReader(Some(historyFile), _: Parser[_], true)
    val reader: Parser[_] => LineReader = if (args.isEmpty) {
      consoleReader
    } else {
      val firstArgAsConnect = args.head.parseInt.map { pid => "connect -q " + pid }
      firstArgAsConnect fold (
        _ => constReader(args :+ "exit").liftReader[Parser[_]],
        cmd =>
          if (args.tail.isEmpty)
            prefixedReader(cmd +: args.tail, consoleReader)
          else
            constReader(cmd +: args.tail :+ "exit").liftReader[Parser[_]]
      )
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

