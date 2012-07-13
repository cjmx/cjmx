package cjmx.cli
package actions

import javax.management.remote.JMXConnector

import cjmx.util.jmx.MBeanQuery


case class DescribeMBeans(query: MBeanQuery, detailed: Boolean) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnector) = {
    val svr = connection.getMBeanServerConnection
    val names = svr.toScala.queryNames(query).toList.sorted
    val info = names map { name => name -> svr.getMBeanInfo(name) }
    context.formatter.formatInfo(info, detailed)
  }
}


