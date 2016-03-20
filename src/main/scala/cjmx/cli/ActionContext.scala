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

