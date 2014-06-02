package cjmx.util.jmx

import javax.management.{ObjectName, QueryExp}
import Beans.{Field,Results,SubqueryName,unnamed}

/** Represents a where for MBeans that match an expression. */
case class MBeanQuery(results: Results, where: Field[Boolean])

object MBeanQuery {
  //def All =
  //  MBeanQuery(Map(unnamed -> ObjectName.WILDCARD), Field.literal(true))
  //def apply(from: ObjectName, where: Field[Boolean] = Field.literal(true), as: SubqueryName = unnamed): MBeanQuery =
  //  MBeanQuery(Map(as -> from), where)
}

