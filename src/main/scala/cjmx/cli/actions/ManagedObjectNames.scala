package cjmx.cli
package actions

import scalaz.syntax.validation._

import javax.management.{ObjectName, QueryExp}
import javax.management.remote.JMXConnector


case class ManagedObjectNames(name: Option[ObjectName], query: Option[QueryExp]) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnector) = {
    connection.getMBeanServerConnection.toScala.queryNames(name, query).toList.sorted.map { _.toString }.success
  }
}

