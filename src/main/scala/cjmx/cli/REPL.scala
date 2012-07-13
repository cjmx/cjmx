package cjmx.cli

import scala.annotation.tailrec

import scalaz._
import Scalaz._
import scalaz.effect._
import scalaz.iteratee._
import IterateeT._

import java.io.PrintWriter
import java.io.PrintStream

import sbt.LineReader
import sbt.complete.Parser

import cjmx.util.jmx.JMX


object REPL {
  def run(reader: Parser[_] => LineReader, out: PrintStream): Int = {
    @tailrec def runR(state: ActionContext): Int = {
      state.runState match {
        case Running =>
          val parser = state.connectionState match {
            case Disconnected => Parsers.Disconnected(JMX.localVMs)
            case Connected(cnx) => Parsers.Connected(cnx)
          }
          def readLine = reader(parser).readLine("> ").fold(some, some("exit")).filter { _.nonEmpty }
          val result = for {
            line <- readLine.success[NonEmptyList[String]]
            parse = (line: String) => Validation.fromEither(Parser.parse(line, parser)).toValidationNel
            action <- line.fold(parse, NoopAction.success)
          } yield action(state)
          val newState = result match {
            case Success((newState, msgs)) =>
              (putStrTo[String](out) %= Iteratee.map((_: String) + "%n".format()) &= msgs).run.unsafePerformIO
              newState
            case Failure(errs) =>
              val lines = errs.list flatMap { _.split('\n') }
              val formatted = lines map { e => "[%serror%s] %s".format(Console.RED, Console.RESET, e) }
              formatted foreach out.println
              state
          }
          runR(newState)

        case Exit(statusCode) =>
          statusCode
      }
    }

    runR(ActionContext())
  }
}
