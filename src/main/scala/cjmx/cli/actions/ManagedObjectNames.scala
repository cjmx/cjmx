package cjmx.cli
package actions

import scalaz.syntax.validation._

import javax.management.{ObjectName, QueryExp}
import scala.collection.JavaConverters._

import cjmx.util.jmx._
import cjmx.util.jmx.Beans.{SubqueryName,Field}

case class ManagedObjectNames(query: MBeanQuery) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnection) = {
    val names = connection.mbeanServer.queryNames(query).toList.sorted
    context.formatter.formatNames(names)
  }
}

object ManagedObjectNames {

  def apply(subqueries: Map[SubqueryName, ObjectName], where: Field[Boolean]): ManagedObjectNames =
    sys.error("todo")
}

