package cjmx.cli
package actions

import cjmx.util.jmx.Attach
import scalaz.stream.Process


case class Connect(vmid: String, quiet: Boolean) extends Action {
  def apply(context: ActionContext) =
    Attach.localConnect(vmid) fold (
      err => emitMessages(err) ++ fail(err),
      cnx => {
        val server = cnx.getMBeanServerConnection
        updateContext(ActionContext.Connect(cnx)) ++
        emitMessageSeq(if (quiet) List() else List(
          "Connected to local virtual machine %s".format(vmid),
          "Connection id: %s".format(cnx.getConnectionId),
          "Default domain: %s".format(server.getDefaultDomain),
          "%d domains registered consisting of %d total MBeans".format(
            server.getDomains.length,
            server.getMBeanCount)
        ))
      }
    )
}

