package cjmx.cli
package actions

import scala.collection.immutable.Seq

import javax.management.Attribute

import cjmx.util.jmx._

case class Query(query: MBeanQuery, projection: Seq[Attribute] => Seq[Attribute] = identity) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnection) = {
    val svr = connection.mbeanServer
    val names = svr.toScala.queryNames(query).toList.sorted
    val namesAndAttrs = names map { name =>
      val info = svr.getMBeanInfo(name)
      val attrNames = info.getAttributes map { _.getName }
      name -> projection(svr.attributes(name, attrNames).toList)
    }
    context.formatter.formatAttributes(namesAndAttrs)
  }
}

