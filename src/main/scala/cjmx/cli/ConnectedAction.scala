package cjmx.cli

import cjmx.util.jmx.JMXConnection

trait ConnectedAction extends Action {
  final def apply(context: ActionContext) =
    applyConnected(context, context.connectionState.asInstanceOf[Connected].connection)
  def applyConnected(context: ActionContext, connection: JMXConnection): ActionResult
}

