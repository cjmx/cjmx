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

package cjmx.cli

import cjmx.util.jmx.JMXConnection

sealed abstract class RunState
object RunState {
  final case object Running extends RunState
  final case class Exit(statusCode: Int) extends RunState
}

sealed abstract class ConnectionState
object ConnectionState {
  final case object Disconnected extends ConnectionState
  final case class Connected(connection: JMXConnection) extends ConnectionState
}

trait ActionContext {
  def runState: RunState
  def connectionState: ConnectionState

  def withRunState(rs: RunState): ActionContext
  def exit(statusCode: Int): ActionContext

  def connected(connection: JMXConnection): ActionContext
  def disconnected: ActionContext

  def formatter: MessageFormatter
  def withFormatter(fmt: MessageFormatter): ActionContext

  def lastStatusCode: Int
  def withStatusCode(statusCode: Int): ActionContext

  def readLine(prompt: String, mask: Option[Char]): Option[String]
}

object ActionContext {
  private case class DefaultActionContext(
    runState: RunState,
    connectionState: ConnectionState,
    formatter: MessageFormatter,
    lastStatusCode: Int,
    lineReader: (String, Option[Char]) => Option[String]
  ) extends ActionContext {
    override def withRunState(rs: RunState) = copy(runState = rs)
    override def exit(statusCode: Int) = withRunState(RunState.Exit(statusCode))
    override def connected(connection: JMXConnection) = copy(connectionState = ConnectionState.Connected(connection))
    override def disconnected = copy(connectionState = ConnectionState.Disconnected)
    override def withFormatter(fmt: MessageFormatter) = copy(formatter = fmt)
    override def withStatusCode(statusCode: Int) = copy(lastStatusCode = statusCode)
    override def readLine(prompt: String, mask: Option[Char]) = lineReader(prompt, mask)
  }

  val noOpLineReader: (String, Option[Char]) => Option[String] = (x, y) => None

  /** Provides an instance of `ActionContext` that does not require input from the console. **/
  def embedded(runState: RunState = RunState.Running, connectionState: ConnectionState = ConnectionState.Disconnected, formatter: MessageFormatter = TextMessageFormatter, statusCode: Int = 0): ActionContext =
    withLineReader(runState, connectionState, formatter, statusCode, lineReader = noOpLineReader)

  def apply(runState: RunState = RunState.Running, connectionState: ConnectionState = ConnectionState.Disconnected, formatter: MessageFormatter = TextMessageFormatter, statusCode: Int = 0): ActionContext =
    embedded(runState, connectionState, formatter, statusCode)

  def withLineReader(runState: RunState = RunState.Running, connectionState: ConnectionState = ConnectionState.Disconnected, formatter: MessageFormatter = TextMessageFormatter, statusCode: Int = 0, lineReader: (String, Option[Char]) => Option[String]): ActionContext =
    DefaultActionContext(runState, connectionState, formatter, statusCode, lineReader)
}

