package cjmx.cli
package actions

import cjmx.util.jmx.JMXConnection


object Disconnect extends ConnectedAction {
  def applyConnected(context: ActionContext, connection: JMXConnection) = {
    connection.dispose()
    (context.disconnected, enumMessages("Disconnected"))
  }
}

