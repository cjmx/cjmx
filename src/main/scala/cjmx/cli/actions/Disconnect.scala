package cjmx.cli
package actions

import scalaz.syntax.validation._

import javax.management.remote.JMXConnector


object Disconnect extends ConnectedAction {
  def applyConnected(context: ActionContext, connection: JMXConnector) = {
    connection.close
    (context.disconnected, enumMessages("Disconnected")).success
  }
}

