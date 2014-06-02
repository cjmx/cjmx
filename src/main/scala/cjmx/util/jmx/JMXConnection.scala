package cjmx.util.jmx

import scala.language.implicitConversions

import java.lang.management.ManagementFactory
import javax.management.MBeanServerConnection
import javax.management.remote.JMXConnector

/** Provides access to a local or remote MBean server. */
abstract class JMXConnection {
  def mbeanServer: MBeanServerConnection
  def dispose()
}

object JMXConnection {
  implicit def connectorToConnection(connector: JMXConnector): JMXConnection = new JMXConnection {
    override def mbeanServer = connector.getMBeanServerConnection
    override def dispose() = connector.close()
  }

  val PlatformMBeanServerConnection: JMXConnection = new JMXConnection {
    override def mbeanServer = ManagementFactory.getPlatformMBeanServer
    override def dispose() {}
  }
}
