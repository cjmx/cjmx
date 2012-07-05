package cjmx.cli
package actions

import scala.collection.JavaConverters._
import scalaz.syntax.show._
import scalaz.syntax.validation._

import javax.management.{Attribute, ObjectName, QueryExp}
import javax.management.remote.JMXConnector

import cjmx.util.jmx._


case class Query(name: Option[ObjectName], query: Option[QueryExp]) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnector) = {
    val svr = connection.getMBeanServerConnection
    val names = svr.toScala.queryNames(name, query).toList.sorted
    val out = new OutputBuilder
    names foreach { name =>
      val info = svr.getMBeanInfo(name)
      val attrNames = info.getAttributes map { _.getName }
      val attrs = svr.attributes(name, attrNames)
      out <+ name.toString
      out <+ ("-" * name.toString.size)
      out indented {
        attrs foreach { attr => out <+ attr.shows }
      }
      out <+ ""
    }
    out.lines.success
  }
}

