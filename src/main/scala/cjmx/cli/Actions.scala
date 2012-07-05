package cjmx.cli

import scala.collection.JavaConverters._

import scalaz._
import Scalaz._
import scalaz.effect.IO
import scalaz.iteratee.EnumeratorT

import com.sun.tools.attach._

import javax.management._
import javax.management.remote.JMXConnector

import cjmx.util.JMX

object Actions {

  case object Quit extends Action {
    override def apply(context: ActionContext) = {
      (context.exit(0), EnumeratorT.empty[String, IO]).success
    }
  }

  case class Connect(vmid: String, quiet: Boolean) extends Action {
    def apply(context: ActionContext) = {
      JMX.localConnect(vmid) match {
        case Success(cnx) =>
          val server = cnx.getMBeanServerConnection
          (context.connected(cnx), enumMessageList(if (quiet) List.empty else List(
            "Connected to local virtual machine %s".format(vmid),
            "Connection id: %s".format(cnx.getConnectionId),
            "Default domain: %s".format(server.getDefaultDomain),
            "%d domains registered consisting of %d total MBeans".format(server.getDomains.length, server.getMBeanCount)
          ))).success
        case Failure(err) =>
          NonEmptyList(err).fail
      }
    }
  }

  case object Disconnect extends ConnectedAction {
    def applyConnected(context: ActionContext, connection: JMXConnector) = {
      connection.close
      (context.disconnected, enumMessages("Disconnected")).success
    }
  }

  case object ListVMs extends SimpleAction {
    def act(context: ActionContext) = {
      val vms = JMX.localVMs
      val longestId = vms.foldLeft(0) { (longest, vm) => longest max vm.id.length }
      val vmStrings = JMX.localVMs map { vm => "%%-%ds %%s".format(longestId).format(vm.id, vm.displayName) }
      vmStrings.success
    }
  }

  case class ManagedObjectNames(name: Option[ObjectName], query: Option[QueryExp]) extends SimpleConnectedAction {
    def act(context: ActionContext, connection: JMXConnector) = {
      val names = connection.getMBeanServerConnection.queryNames(name.orNull, query.orNull).asScala
      names.toList.sorted.map { _.toString }.success
    }
  }

  case class InspectMBeans(name: Option[ObjectName], query: Option[QueryExp], detailed: Boolean) extends SimpleConnectedAction {
    def act(context: ActionContext, connection: JMXConnector) = {
      val svr = connection.getMBeanServerConnection
      val names = svr.queryNames(name.orNull, query.orNull).asScala.toList.sorted

      val out = new OutputBuilder
      val info = names map { name => name -> svr.getMBeanInfo(name) }
      for ((name, inf) <- info) {
        val nameLine = "Object name: %s".format(name)
        out <+ nameLine
        out <+ ("-" multiply nameLine.size)
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


  case class Query(query: ObjectName) extends SimpleConnectedAction {
    def act(context: ActionContext, connection: JMXConnector) = {
      // TODO
      List.empty.success
    }
  }
}

