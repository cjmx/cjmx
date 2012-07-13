package cjmx.cli

import scalaz.syntax.show._
import scalaz.syntax.validation._

import javax.management.{ObjectName, Attribute, MBeanInfo}

import cjmx.util.jmx.{JMX, JMXTags}


object TextMessageFormatter extends MessageFormatter {

  override def formatNames(names: Seq[ObjectName]) = {
    names.map { _.toString }
  }

  override def formatAttributes(attrsByName: Seq[(ObjectName, Seq[Attribute])]) = {
    val out = new OutputBuilder
    attrsByName foreach { case (name, attrs) =>
      out <+ name.toString
      out <+ ("-" * name.toString.size)
      out indented {
        attrs foreach { attr => out <+ attr.shows }
      }
      out <+ ""
    }
    out.lines
  }

  override def formatInfo(info: Seq[(ObjectName, MBeanInfo)], detailed: Boolean) = {
    val out = new OutputBuilder
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
    out.lines
  }

  override def formatInvocationResults(namesAndResults: Seq[(ObjectName, Either[String, AnyRef])]) = {
    val out = new OutputBuilder
    namesAndResults foreach { case (name, result) =>
      out <+ "%s: %s".format(name, result.fold(identity, v => JMXTags.Value(v).shows))
    }
    out.lines
  }
}

