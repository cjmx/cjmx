package cjmx.util.jmx

import scala.annotation.tailrec
import scala.collection.JavaConverters._

import scalaz._
import Scalaz._

import javax.management._
import javax.management.openmbean._

/** Provides utilities for working with JMX. */
object JMX {

  implicit val typeShow: Show[String @@ JMXTags.Type] = Show.shows(humanizeType)
  implicit val valueShow: Show[AnyRef @@ JMXTags.Value] = Show.shows(humanizeValue)
  implicit val attributeShow: Show[Attribute] = Show.shows(humanizeAttribute)

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
        keysAndValues.
          map { case (k, v) => "%s: %s".format(k, JMXTags.Value(v).shows) }.
          mkString(newline, newline, "") |> indentLines(2)
      case arr: Array[_] => arr.map { case v: AnyRef => JMXTags.Value(v).shows }.mkString("[", ", ", "]")
      case n if n eq null => "null"
      case other => other.toString
    }
  }

  def humanizeAttribute(a: Attribute): String = {
    "%s: %s".format(a.getName, JMXTags.Value(a.getValue).shows)
  }

  def typeToClass(cl: ClassLoader)(t: String): Option[Class[_]] = {
    t match {
      case "boolean" => Some(classOf[java.lang.Boolean])
      case "byte" => Some(classOf[java.lang.Byte])
      case "char" => Some(classOf[java.lang.Character])
      case "short" => Some(classOf[java.lang.Short])
      case "int" => Some(classOf[java.lang.Integer])
      case "long" => Some(classOf[java.lang.Long])
      case "float" => Some(classOf[java.lang.Float])
      case "double" => Some(classOf[java.lang.Double])
      case other =>
        try Some(Class.forName(t, true, cl))
        catch {
          case e: ClassNotFoundException => None
        }
    }
  }

  @tailrec final def extractValue(value: AnyRef, names: Seq[String]): Option[AnyRef] = {
    if (names.isEmpty)
      Some(value)
    else value match {
      case cd: CompositeData =>
        val nextName = names.head
        Option(cd.get(nextName)) match {
          case Some(nv) => extractValue(nv, names.tail)
          case None => None
        }
      case _ => None
    }
  }

  private val newline = "%n".format()
  private def indentLines(indent: Int)(s: String): String =
    s.split(newline).map { s => (" " * indent) + s }.mkString(newline)
}

