package cjmx.cli
package actions

import scalaz.syntax.validation._

import javax.management.{ObjectName, QueryExp}
import javax.management.remote.JMXConnector

import cjmx.util.JMX


case class InspectMBeans(name: Option[ObjectName], query: Option[QueryExp], detailed: Boolean) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnector) = {
    val svr = connection.getMBeanServerConnection
    val names = svr.toScala.queryNames(name, query).toList.sorted

    val out = new OutputBuilder
    val info = names map { name => name -> svr.getMBeanInfo(name) }
    for ((name, inf) <- info) {
      val nameLine = "Object name: %s".format(name)
      out <+ nameLine
      out <+ ("-" * nameLine.size)
      out <+ "Description: %s".format(inf.getDescription)
      out <+ ""

      val attributes = inf.getAttributes
      if (attributes.nonEmpty) {
        out <+ "Attributes:"
        out indented {
          attributes.foreach { attr =>
            out <+ "%s: %s".format(attr.getName, JMX.humanizeType(attr.getType))
            if (detailed) out.indented {
              out <+ "Description: %s".format(attr.getDescription)
            }
          }
        }
        out <+ ""
      }

      val operations = inf.getOperations
      if (operations.nonEmpty) {
        out <+ "Operations:"
        out indented {
          operations foreach { op =>
            out <+ "%s(%s): %s".format(
              op.getName,
              op.getSignature.map { pi => "%s: %s".format(pi.getName, JMX.humanizeType(pi.getType)) }.mkString(", "),
              JMX.humanizeType(op.getReturnType))
            if (detailed) out.indented {
              out <+ "Description: %s".format(op.getDescription)
            }
          }
        }
        out <+ ""
      }

      val notifications = inf.getNotifications
      if (notifications.nonEmpty) {
        out <+ "Notifications:"
        out indented {
          notifications foreach { nt =>
            out <+ nt.getName
            if (detailed) out.indented {
              out <+ "Description: %S".format(nt.getDescription)
              out <+ "Notification types:"
              out indented {
                nt.getNotifTypes foreach { out <+ _ }
              }
            }
          }
        }
        out <+ ""
      }
    }
    out.lines.success
  }
}


