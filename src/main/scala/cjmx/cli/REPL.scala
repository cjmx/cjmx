package cjmx
package cli

import scala.annotation.tailrec
import scala.util.control.NonFatal

import java.io.PrintStream
import java.rmi.UnmarshalException

import sbt.{ LineReader, SimpleReader }
import sbt.complete.Parser

import cjmx.util.jmx.Attach


object REPL {
  def run(reader: Parser[_] => LineReader, out: PrintStream): Int = {
    @tailrec def runR(state: ActionContext): Int = {
      state.runState match {
        case RunState.Running =>
          val parser = state.connectionState match {
            case ConnectionState.Disconnected => Parsers.Disconnected(Attach.localVMIDs)
            case ConnectionState.Connected(cnx) => Parsers.Connected(cnx.mbeanServer)
          }
          def readLine: Option[String] =
            reader(parser).readLine("> ").fold(Some("exit"): Option[String])(s => Some(s)).filter { _.nonEmpty }
          val result: Either[String, ActionResult] = for {
            line <- Right(readLine)
            parse = (line: String) => Parser.parse(line, parser)
            action <- line.fold(Right(NoopAction: Action): Either[String, Action])(parse)
            res <- {
              try Right(action(state))
              catch {
                case NonFatal(t) => Left(humanizeActionException(t))
              }
            }
          } yield res
          val newState: ActionContext = result match {
            case Left(err) =>
              val lines = err.split('\n')
              val formatted = lines map { e => "[%serror%s] %s".format(Console.RED, Console.RESET, e) }
              formatted foreach out.println
              state.withStatusCode(1)
            case Right(ActionResult(newState, output)) =>
              output.map { _ + newline }.foreach { msg => out.write(msg.getBytes) }
              newState
          }
          runR(newState)

        case RunState.Exit(statusCode) =>
          statusCode
      }
    }

    runR(ActionContext.withLineReader(lineReader = SimpleReader.readLine(_, _)))
  }

  private val newline = "%n".format()
  private val addNewline = (_: String) + newline

  private def humanizeActionException(t: Throwable): String = t match {
    case e: UnmarshalException
      if e.getCause != null &&
         e.getCause.isInstanceOf[ClassNotFoundException] &&
         e.getCause.getMessage.contains("cjmx.ext.") =>
      "Command cannot be executed because it requires attached process to have the cjmx-ext JAR on its classpath."
    case e: ClassNotFoundException => "Nope"
    case other => other.getClass.getName + " = " + other.getMessage
  }
}
