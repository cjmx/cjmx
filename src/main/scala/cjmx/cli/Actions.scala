package cjmx.cli

import scalaz._
import Scalaz._

import com.sun.tools.attach._
import scala.collection.JavaConverters._

import javax.management._
import javax.management.remote.JMXConnector

import cjmx.util.JMX

object Actions {

  case object Quit extends Action {
    override def apply(context: ActionContext) = {
      (context.exit(0), Seq.empty).success
    }
  }

  case class Connect(vmid: String) extends Action {
    def apply(context: ActionContext) = {
      JMX.localConnect(vmid) match {
        case Success(cnx) =>
          val server = cnx.getMBeanServerConnection
          (context.connected(cnx), Seq(
            "Connected to local virtual machine %s".format(vmid),
            "Connection id: %s".format(cnx.getConnectionId),
            "%d domains registered consisting of %d total MBeans".format(server.getDomains.length, server.getMBeanCount)
          )).success
        case Failure(err) =>
          NonEmptyList(err).fail
      }
    }
  }

  case object Disconnect extends ConnectedAction {
    def applyConnected(context: ActionContext, connection: JMXConnector) = {
      connection.close
      (context.disconnected, Seq("Disconnected")).success
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
      names.toSeq.sorted.map { _.toString }.success
    }
  }

  case class Query(query: ObjectName) extends SimpleConnectedAction {
    def act(context: ActionContext, connection: JMXConnector) = {
      // TODO
      Seq.empty.success
    }
  }
}

