package cjmx.cli
package actions

import cjmx.util.jmx.JMXConnection
import scalaz.concurrent.Task
import scalaz.stream.Process

object Disconnect extends ConnectedAction {
  def applyConnected(context: ActionContext, connection: JMXConnection) =
    emitMessages("Disconnected") ++
    updateContext(ActionContext.Disconnect) onComplete (
    Process.eval_(Task.delay { connection.dispose() }))
}

