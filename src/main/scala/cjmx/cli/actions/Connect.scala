package cjmx.cli
package actions

import scalaz._
import scalaz.iteratee.EnumeratorT.enumOne

import cjmx.util.jmx.Attach


case class Connect(vmid: String, quiet: Boolean) extends Action {
  def apply(context: ActionContext) = {
    Attach.localConnect(vmid) match {
      case Success(cnx) =>
        val server = cnx.getMBeanServerConnection
        (context.connected(cnx), enumMessageList(if (quiet) List.empty else List(
          "Connected to local virtual machine %s".format(vmid),
          "Connection id: %s".format(cnx.getConnectionId),
          "Default domain: %s".format(server.getDefaultDomain),
          "%d domains registered consisting of %d total MBeans".format(server.getDomains.length, server.getMBeanCount)
        )))
      case Failure(err) =>
        (context.withStatusCode(1), enumOne(err))
    }
  }
}

