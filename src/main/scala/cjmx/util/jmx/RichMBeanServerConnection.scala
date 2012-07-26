package cjmx.util.jmx

import scala.collection.JavaConverters._

import scalaz.syntax.Ops

import java.rmi.UnmarshalException
import javax.management._


trait RichMBeanServerConnection extends Ops[MBeanServerConnection] {

  def toScala = this

  def queryNames(name: Option[ObjectName], query: Option[QueryExp]): Set[ObjectName] =
    self.queryNames(name.orNull, query.orNull).asScala.toSet

  def queryNames(query: MBeanQuery): Set[ObjectName] =
    queryNames(query.from, query.where)

  def mbeanInfo(name: ObjectName): Option[MBeanInfo] =
    Option(self.getMBeanInfo(name))

  def attribute(name: ObjectName, attributeName: String): Option[Attribute] =
    try Some(new Attribute(attributeName, self.getAttribute(name, attributeName)))
    catch {
      case (_: UnmarshalException | _: JMException) =>
        None
    }

  def attributes(name: ObjectName, attributeNames: Array[String]): Seq[Attribute] =
    try self.getAttributes(name, attributeNames).asScala.toSeq.asInstanceOf[Seq[Attribute]]
    catch {
      case (_: UnmarshalException | _: JMException) =>
        attributeNames map { attrName =>
          attribute(name, attrName).getOrElse(new Attribute(attrName, "unavailable"))
        }
    }
}

trait ToRichMBeanServerConnection {
  implicit def enrichMBeanServerConnection(svr: MBeanServerConnection): RichMBeanServerConnection =
    new RichMBeanServerConnection { def self = svr }
}
