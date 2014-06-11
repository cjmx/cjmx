package cjmx.cli

import cjmx.util.jmx.JMXConnection

sealed trait RunState
final case object Running extends RunState
final case class Exit(statusCode: Int) extends RunState

sealed trait ConnectionState
final case object Disconnected extends ConnectionState
final case class Connected(connection: JMXConnection) extends ConnectionState

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
}

object ActionContext {
  private case class DefaultActionContext(
    runState: RunState,
    connectionState: ConnectionState,
    formatter: MessageFormatter,
    lastStatusCode: Int
  ) extends ActionContext {
    override def withRunState(rs: RunState) = copy(runState = rs)
    override def exit(statusCode: Int) = withRunState(Exit(statusCode))
    override def connected(connection: JMXConnection) = copy(connectionState = Connected(connection))
    override def disconnected = copy(connectionState = Disconnected)
    override def withFormatter(fmt: MessageFormatter) = copy(formatter = fmt)
    override def withStatusCode(statusCode: Int) = copy(lastStatusCode = statusCode)
  }

  def apply(runState: RunState = Running, connectionState: ConnectionState = Disconnected, formatter: MessageFormatter = TextMessageFormatter, statusCode: Int = 0): ActionContext =
    DefaultActionContext(runState, connectionState, formatter, statusCode)
}

