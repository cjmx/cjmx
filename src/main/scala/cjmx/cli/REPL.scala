package cjmx.cli

import scala.annotation.tailrec

import scalaz._
import scalaz.concurrent.Task
import scalaz.Free.Trampoline
import scalaz.std.option._
import scalaz.syntax.either._
import scalaz.syntax.nel._
import scalaz.syntax.std.either._
import scalaz.syntax.std.option._
import scalaz.stream.{Process, Sink}

import java.io.PrintWriter
import java.io.PrintStream
import java.rmi.UnmarshalException

import sbt.{ LineReader, SimpleReader}
import sbt.complete.Parser

import cjmx.util.jmx.{ Attach, JMX }


object REPL {
  def run(reader: Parser[_] => LineReader, out: PrintStream): Int = {
    // A `Sink` that writes to `out`
    val printer: Sink[Task,String] =
      Process.constant((m: String) => Task.delay(out.write(m.getBytes)))

    @tailrec def runR(state: ActionContext): Int = {
      state.runState match {
        case Running =>
          val parser = state.connectionState match {
            case Disconnected => Parsers.Disconnected(Attach.localVMIDs)
            case Connected(cnx) => Parsers.Connected(cnx.mbeanServer)
          }
          def readLine = reader(parser).readLine("> ").cata(some, some("exit")).filter { _.nonEmpty }
          val result = for {
            line <- readLine.right[NonEmptyList[String]]
            parse = (line: String) => Parser.parse(line, parser).disjunction.bimap(_.wrapNel, identity)
            action <- line.cata(parse, NoopAction.right)
            res <- \/.fromTryCatchNonFatal(action(state)).bimap(t => humanizeActionException(t).wrapNel, identity)
          } yield res
          val newState = result fold (
            errs => {
              val lines = errs.list flatMap { _.split('\n') }
              val formatted = lines map { e => "[%serror%s] %s".format(Console.RED, Console.RESET, e) }
              formatted foreach out.println
              state.withStatusCode(1)
            },
            { case (newState, msgs) => msgs.map { _ + newline }.to(printer).run.run
              newState
            }
          )
          runR(newState)

        case Exit(statusCode) =>
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
