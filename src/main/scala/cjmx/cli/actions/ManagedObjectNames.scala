package cjmx.cli
package actions

import scalaz.syntax.validation._

import javax.management.{ObjectName, QueryExp}
import javax.management.remote.JMXConnector

import cjmx.util.jmx.MBeanQuery


case class ManagedObjectNames(query: MBeanQuery) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnector) = {
    val names = connection.getMBeanServerConnection.toScala.queryNames(query).toList.sorted
    context.formatter.formatNames(names).success
  }
}

