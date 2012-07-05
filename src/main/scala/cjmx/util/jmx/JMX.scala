package cjmx.util.jmx

import scala.collection.JavaConverters._

import scalaz._
import Scalaz._

import javax.management._
import javax.management.openmbean._


object JMXTags {
  sealed trait Type
  def Type(t: String): String @@ Type = Tag[String, Type](t)

  sealed trait Value
  def Value(v: AnyRef): AnyRef @@ Value = Tag[AnyRef, Value](v)
}


trait JMXFunctions {
  import JMXInstances._

  def humanizeType(t: String): String = {
    try Class.forName(t).getSimpleName
    catch {
      case cnfe: ClassNotFoundException => t
    }
  }

  def humanizeValue(v: AnyRef): String = {
    v match {
      case composite: CompositeData =>
        val keys = composite.getCompositeType.keySet.asScala.toSeq.sorted
        val keysAndValues = keys zip composite.getAll(keys.toArray).toSeq
        val newline = "%n".format()
        keysAndValues.map { case (k, v) => "  %s: %s".format(k, JMXTags.Value(v).shows) }.mkString(newline, newline, "")
      case arr: Array[_] => java.util.Arrays.toString(arr.asInstanceOf[Array[AnyRef]])
      case n if n eq null => "null"
      case other => other.toString
    }
  }

  def humanizeAttribute(a: Attribute): String = {
    "%s: %s".format(a.getName, JMXTags.Value(a.getValue).shows)
  }
}

object JMXFunctions extends JMXFunctions


trait JMXInstances {
  import JMXFunctions._

  implicit val typeShow: Show[String @@ JMXTags.Type] = Show.shows(humanizeType)
  implicit val valueShow: Show[AnyRef @@ JMXTags.Value] = Show.shows(humanizeValue)
  implicit val attributeShow: Show[Attribute] = Show.shows(humanizeAttribute)
}

object JMXInstances extends JMXInstances


trait JMX
  extends JMXInstances
  with JMXFunctions
  with ToRichMBeanServerConnection
  with Attach

object JMX extends JMX

