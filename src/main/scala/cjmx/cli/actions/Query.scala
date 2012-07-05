package cjmx.cli
package actions

import scalaz.syntax.validation._

import javax.management.{ObjectName, QueryExp}
import javax.management.remote.JMXConnector

import cjmx.util.JMX


case class Query(query: ObjectName) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnector) = {
    // TODO
    List.empty.success
  }
}

