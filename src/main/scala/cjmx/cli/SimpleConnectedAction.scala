package cjmx.cli

import scala.collection.immutable.Seq

import cjmx.util.jmx.JMXConnection

trait SimpleConnectedAction extends ConnectedAction {
  final def applyConnected(context: ActionContext, connection: JMXConnection) = {
    val output = act(context, connection)
    ActionResult(context.withStatusCode(0), output)
  }
  def act(context: ActionContext, connection: JMXConnection): Seq[String]
}

