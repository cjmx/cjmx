package cjmx.cli
package actions

import cjmx.util.jmx._

case class ManagedObjectNames(query: MBeanQuery) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnection) = {
    val names = connection.mbeanServer.toScala.queryNames(query).toList.sorted
    context.formatter.formatNames(names)
  }
}

