package cjmx.cli

import cjmx.util.jmx.JMXConnection

trait ConnectedAction extends Action {
  final def apply(context: ActionContext) = context.connectionState match {
    case ConnectionState.Connected(cnx) =>
      applyConnected(context, cnx)
    case ConnectionState.Disconnected =>
      ActionResult(context, List("Not connected"))
  }
  def applyConnected(context: ActionContext, connection: JMXConnection): ActionResult
}
