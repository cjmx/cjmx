package cjmx.cli
package actions

import scalaz._
import scalaz.syntax.validation._

import cjmx.util.JMX


case class Connect(vmid: String, quiet: Boolean) extends Action {
  def apply(context: ActionContext) = {
    JMX.localConnect(vmid) match {
      case Success(cnx) =>
        val server = cnx.getMBeanServerConnection
        (context.connected(cnx), enumMessageList(if (quiet) List.empty else List(
          "Connected to local virtual machine %s".format(vmid),
          "Connection id: %s".format(cnx.getConnectionId),
          "Default domain: %s".format(server.getDefaultDomain),
          "%d domains registered consisting of %d total MBeans".format(server.getDomains.length, server.getMBeanCount)
        ))).success
      case Failure(err) =>
        NonEmptyList(err).fail
    }
  }
}

