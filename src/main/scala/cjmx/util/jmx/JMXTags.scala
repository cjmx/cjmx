package cjmx.util.jmx

import scalaz.{ @@, Tag }

/** Type tags related to JMX. */
object JMXTags {
  /** String that represents a JMX type name. */
  sealed trait Type
  def Type(t: String): String @@ Type = Tag[String, Type](t)

  /** Object that represents the value of a JMX attribute. */
  sealed trait Value
  def Value(v: AnyRef): AnyRef @@ Value = Tag[AnyRef, Value](v)

  /** String that represents a virtual maching PID. */
  sealed trait VMID
  def VMID(id: String): String @@ VMID = Tag[String, VMID](id)
}
