package cjmx.cli
package actions

import scala.collection.JavaConverters._
import scalaz.syntax.show._

import javax.management.{Attribute, ObjectName, QueryExp}

import cjmx.util.jmx._
import cjmx.util.jmx.Beans.Projection


case class Query(query: MBeanQuery, projection: Projection) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnection) = {
    sys.error("todo")
    //val svr = connection.mbeanServer
    //val names = svr.toScala.queryNames(query).toList.sorted
    //val namesAndAttrs = names map { name =>
    //  val info = svr.getMBeanInfo(name)
    //  val attrNames = info.getAttributes map { _.getName }
    //  name -> projection(svr.attributes(name, attrNames))
    //}
    //context.formatter.formatAttributes(namesAndAttrs)
  }
}

