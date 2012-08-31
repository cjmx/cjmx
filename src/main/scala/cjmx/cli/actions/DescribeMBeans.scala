package cjmx.cli
package actions

import cjmx.util.jmx.{JMXConnection, MBeanQuery}


case class DescribeMBeans(query: MBeanQuery, detailed: Boolean) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnection) = {
    val svr = connection.mbeanServer
    val names = svr.toScala.queryNames(query).toList.sorted
    val info = names map { name => name -> svr.getMBeanInfo(name) }
    context.formatter.formatInfo(info, detailed)
  }
}


