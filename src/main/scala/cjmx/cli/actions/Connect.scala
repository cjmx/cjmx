package cjmx.cli
package actions

import cjmx.util.jmx.Attach
import cjmx.util.jmx.JMX._

import scalaz.stream.Process


case class Connect(vmid: String, quiet: Boolean) extends Action {
  def apply(context: ActionContext) = {
    Attach.localConnect(vmid) fold (
      err => (context.withStatusCode(1), Process.emit(err)),
      cnx => {
        val server = cnx.getMBeanServerConnection
        (context.connected(cnx), enumMessageList(if (quiet) List.empty else List(
          "Connected to local virtual machine %s".format(vmid),
          "Connection id: %s".format(cnx.getConnectionId),
          "Default domain: %s".format(server.getDefaultDomain),
          "%d domains registered consisting of %d total MBeans".format(server.getDomains.length, server.getMBeanCount)
        )))
      }
    )
  }
}

