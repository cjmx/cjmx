package cjmx.cli

import cjmx.util.jmx.JMXConnection

trait SimpleConnectedAction extends ConnectedAction {
  final def applyConnected(context: ActionContext, connection: JMXConnection) = {
    val msgs = enumMessageSeq(act(context, connection))
    (context.withStatusCode(0), msgs)
  }
  def act(context: ActionContext, connection: JMXConnection): Seq[String]
}

