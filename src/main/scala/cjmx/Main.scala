package cjmx

import java.io.PrintWriter

import sbt.{FullReader, Path}
import Path._

import cjmx.cli.REPL

object Main extends App {
  val historyFile = (userHome / ".cjmx.history").asFile
  val statusCode = REPL.run(new FullReader(Some(historyFile), _), new PrintWriter(Console.out, true))
  System.exit(statusCode)
}

