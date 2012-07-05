package cjmx.util

import scala.collection.JavaConverters._

import scalaz.syntax.Ops

import javax.management._


trait RichMBeanServerConnection extends Ops[MBeanServerConnection] {

  def toScala = this

  def queryNames(name: Option[ObjectName], query: Option[QueryExp]): Set[ObjectName] =
    self.queryNames(name.orNull, query.orNull).asScala.toSet
}

trait ToRichMBeanServerConnection {
  implicit def enrichMBeanServerConnection(svr: MBeanServerConnection): RichMBeanServerConnection =
    new RichMBeanServerConnection { def self = svr }
}
