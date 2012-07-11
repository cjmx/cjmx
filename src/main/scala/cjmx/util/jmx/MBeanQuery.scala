package cjmx.util.jmx

import javax.management.{ObjectName, QueryExp}

/** Represents a where for MBeans that match an expression. */
case class MBeanQuery(from: Option[ObjectName], where: Option[QueryExp])

object MBeanQuery {
  def All = MBeanQuery(None, None)
  def apply(from: ObjectName): MBeanQuery = MBeanQuery(Some(from), None)
  def apply(where: QueryExp): MBeanQuery = MBeanQuery(None, Some(where))
  def apply(from: ObjectName, where: QueryExp): MBeanQuery = MBeanQuery(Some(from), Some(where))
}

