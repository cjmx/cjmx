package cjmx.util.jmx

import scala.collection.JavaConverters._

import scalaz.syntax.Ops

import javax.management._


trait RichMBeanServerConnection extends Ops[MBeanServerConnection] {

  def toScala = this

  def queryNames(name: Option[ObjectName], query: Option[QueryExp]): Set[ObjectName] =
    self.queryNames(name.orNull, query.orNull).asScala.toSet

  def queryNames(query: MBeanQuery): Set[ObjectName] =
    queryNames(query.from, query.where)

  def attributes(name: ObjectName, attributeNames: Array[String]): Seq[Attribute] =
    self.getAttributes(name, attributeNames).asScala.toSeq.asInstanceOf[Seq[Attribute]]
}

trait ToRichMBeanServerConnection {
  implicit def enrichMBeanServerConnection(svr: MBeanServerConnection): RichMBeanServerConnection =
    new RichMBeanServerConnection { def self = svr }
}
