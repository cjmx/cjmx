package cjmx.util.jmx

import scala.annotation.tailrec
import scala.collection.JavaConverters._

import javax.management._
import javax.management.openmbean._

/** Provides utilities for working with JMX. */
object JMX {

  case class VMID(value: String) {
    override def toString = value
  }

  case class JType(value: String) {
    override def toString = {
      try Class.forName(value).getSimpleName
      catch {
        case cnfe: ClassNotFoundException => value
      }
    }
  }

  case class JValue(value: AnyRef) {
    override def toString = value match {
      case composite: CompositeData =>
        val keys = composite.getCompositeType.keySet.asScala.toSeq.sorted
        val keysAndValues = keys zip composite.getAll(keys.toArray).toSeq
        indentLines(2) {
          keysAndValues.
            map { case (k, v) => "%s: %s".format(k, JValue(v)) }.
            mkString(newline, newline, "")
        }
      case arr: Array[_] => arr.map { case v: AnyRef => JValue(v) }.mkString("[", ", ", "]")
      case n if n eq null => "null"
      case tds: TabularDataSupport =>

        val tabularType = tds.getTabularType
        val compositeType = tabularType.getRowType
        val keys = compositeType.keySet.asScala

        val lines = tds.getTabularType.getIndexNames.asScala.toList match {
          // Optimize tables with single key
          case uniqueKey :: Nil =>
            val humanizedMap = tds.values.asScala.toList collect { case value: CompositeData =>
              val strKey = JKey(value.get(uniqueKey)).toString
              val rest = (keys - uniqueKey).toList
              rest match  {
                case singleKey :: Nil => strKey -> value.get(singleKey)
                case _                => strKey -> value.getAll(rest.toArray)
              }
            }
            humanizedMap.sortBy { _._1 }.map { case (key, value: AnyRef) =>
              s"${key}: ${JValue(value)}"
            }

          case multipleKeys =>
            tds.asScala.toList.map { case (_, value) =>
              JValue(value).toString
            }
        }
        indentLines(2) { newline + lines.mkString(newline) }

      case other => other.toString

    }
  }

  case class JKey(value: AnyRef) {
    override def toString = value match {
      case compositeKey: CompositeData =>
        val keys = compositeKey.getCompositeType.keySet.asScala.toSeq.sorted
        val keysAndValues = keys.map { k => s"$k: ${compositeKey.get(k)}" }
        val typeName = compositeKey.getCompositeType.getTypeName

        if (typeName.endsWith("MXBean")) {
          val shortName = typeName.split("\\.").last.replace("MXBean", "")
          s"$shortName(${keysAndValues.mkString(", ")})"

        } else {
          s"$typeName(${keysAndValues.mkString(", ")})"
        }

      case other => other.toString
    }
  }

  case class JAttribute(a: Attribute) {
    override def toString =
      "%s: %s".format(a.getName, JValue(a.getValue))
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

