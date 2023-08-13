/*
 * Copyright (c) 2012, cjmx
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package cjmx
package cli

import scala.annotation.tailrec
import scala.util.control.NonFatal

import java.io.PrintStream
import java.rmi.UnmarshalException

import sbt.internal.util.{ LineReader, SimpleReader }
import sbt.internal.util.complete.Parser

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
