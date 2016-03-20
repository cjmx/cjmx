package cjmx.cli
package actions

import cjmx.util.jmx.Attach

case class Connect(vmid: String, quiet: Boolean) extends Action {
  def apply(context: ActionContext) = {
    Attach.localConnect(vmid) fold (
      err => ActionResult(context.withStatusCode(1), List(err)),
      cnx => {
        val server = cnx.getMBeanServerConnection
        ActionResult(context.connected(cnx), if (quiet) List.empty else List(
          s"Connected to local virtual machine ${vmid}",
          s"Connection id: ${cnx.getConnectionId}",
          s"Default domain: ${server.getDefaultDomain}",
          s"${server.getDomains.length} domains registered consisting of ${server.getMBeanCount} total MBeans"
        ))
      }
    )
  }
}

