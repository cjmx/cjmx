package cjmx.cli
package actions

import scala.collection.JavaConverters._
import scalaz.syntax.show._

import javax.management.{Attribute, ObjectName, QueryExp}
import javax.management.remote.JMXConnector

import cjmx.util.jmx._


case class Query(query: MBeanQuery, projection: Seq[Attribute] => Seq[Attribute] = identity) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnector) = {
    val svr = connection.getMBeanServerConnection
    val names = svr.toScala.queryNames(query).toList.sorted
    val namesAndAttrs = names map { name =>
      val info = svr.getMBeanInfo(name)
      val attrNames = info.getAttributes map { _.getName }
      name -> projection(svr.attributes(name, attrNames))
    }
    context.formatter.formatAttributes(namesAndAttrs)
  }
}

