package cjmx.cli

import scala.annotation.tailrec

import scalaz._
import scalaz.concurrent.Task
import scalaz.Free.Trampoline
import scalaz.std.option._
import scalaz.syntax.either._
import scalaz.syntax.std.either._
import scalaz.syntax.nel._
import scalaz.syntax.std.option._
import scalaz.stream.{async, Process, Sink}

import java.io.PrintWriter
import java.io.PrintStream
import java.rmi.UnmarshalException

import sbt.LineReader
import sbt.complete.Parser

import cjmx.util.jmx.JMX
import cjmx.util.MoreEnumerators.untilNone


object REPL {

  def run(reader: Parser[_] => LineReader, out: PrintStream): Int = {
    // A `Sink` that writes to `out`
    val printer: Sink[Task,String] =
      scalaz.stream.io.channel((m: String) => Task.delay(out.write(m.getBytes)))

    val ctxSignal = async.signal[ActionContext]; ctxSignal.value.set(ActionContext())

    ctxSignal.continuous.flatMap { ctx =>
      val parser = ctx.connectionState match {
        case None => Parsers.Disconnected(JMX.localVMs)
        case Some(cnx) => Parsers.Connected(cnx.mbeanServer)
      }
      def readLine = reader(parser).readLine("> ").cata(some, some("exit")).filter { _.nonEmpty }
      val result = for {
        line <- readLine.right[NonEmptyList[String]]
        parse = (line: String) => Parser.parse(line, parser).disjunction.bimap(_.wrapNel, identity)
        action <- line.cata(parse, NoopAction.right)
        res <- \/.fromTryCatch(action(ctx)).bimap(t => humanizeActionException(t).wrapNel, identity)
      } yield res
      val output: Process[Task,String] = result.fold(
        errs => {
          val lines = errs.list flatMap { _.split('\n') }
          val formatted = lines map { e => "[%serror%s] %s".format(Console.RED, Console.RESET, e) }
          formatted foreach println
          Process.eval_ { ctxSignal.compareAndSet(_.map(_.withLastStatusCode(1))) }
        },
        msgs => msgs.flatMap { _.fold(
          modifyCtx => Process.eval_ { ctxSignal.compareAndSet(_.map(modifyCtx)) },
          msg => msg.map(Process.emit(_)).getOrElse { Process.eval_ { ctxSignal.close }}
        )}
      ) ++ Process.eval_ { ctxSignal.compareAndSet(_.map(_.withLastStatusCode(0))) } // only run on success
      output.attempt { err =>
        Process.eval_(ctxSignal.compareAndSet(_.map(_.withLastStatusCode(1)))) ++
        Process.emit(humanizeActionException(err))
      }.map(_.fold(addNewline,addNewline)).to(printer)
    }.run.run

    ctxSignal.get.run.lastStatusCode
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
