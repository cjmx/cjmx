package cjmx.cli
package actions

import cjmx.util.jmx.JMXConnection


object Disconnect extends ConnectedAction {
  def applyConnected(context: ActionContext, connection: JMXConnection) = {
    connection.dispose()
    ActionResult(context.disconnected, List("Disconnected"))
  }
}

