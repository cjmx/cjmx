package cjmx.cli

import cjmx.util.jmx.JMXConnection

case class ActionContext(
    connectionState: Option[JMXConnection] = None,
    formatter: MessageFormatter = TextMessageFormatter,
    lastStatusCode: Int = 0) {
  def connect(c: JMXConnection): ActionContext = copy(connectionState = Some(c))
  def withFormatter(f: MessageFormatter): ActionContext = copy(formatter = f)
  def withLastStatusCode(i: Int): ActionContext = copy(lastStatusCode = i)
}

object ActionContext {
  /**
   * A restricted `ActionContext => ActionContext`. In particular,
   * disallows setting the status code.
   */
  trait Delta extends (ActionContext => ActionContext) {
    def apply(c: ActionContext) = this match {
      case Connect(cnx) => c.copy(connectionState = Some(cnx))
      case Disconnect => c.copy(connectionState = None)
      case Format(f) => c.copy(formatter = f)
    }
  }
  case class Connect(c: JMXConnection) extends Delta
  case object Disconnect extends Delta
  case class Format(f: MessageFormatter) extends Delta

  def applyDeltas(s: Seq[Delta]): ActionContext => ActionContext =
    ctx => s.foldLeft(ctx)((ctx,d) => d(ctx))
}
