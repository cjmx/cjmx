/*
 * Copyright (c) 2012, cjmx
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package cjmx.util.jmx

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

import javax.management.*
import javax.management.openmbean.*

/** Provides utilities for working with JMX. */
object JMX:

  case class VMID(value: String):
    override def toString = value

  case class JType(value: String):
    override def toString =
      try Class.forName(value).getSimpleName
      catch case cnfe: ClassNotFoundException => value

  case class JValue(value: AnyRef):
    override def toString: String = value match
      case composite: CompositeData =>
        val keys = composite.getCompositeType.keySet.asScala.toSeq.sorted
        val keysAndValues = keys.zip(composite.getAll(keys.toArray).toSeq)
        indentLines(2):
          keysAndValues
            .map { case (k, v) => "%s: %s".format(k, JValue(v)) }
            .mkString(newline, newline, "")
      case arr: Array[? <: AnyRef] =>
        arr.map { case v: AnyRef => JValue(v) }.mkString("[", ", ", "]")
      case n if n eq null => "null"
      case tds: TabularDataSupport =>
        val tabularType = tds.getTabularType
        val compositeType = tabularType.getRowType
        val keys = compositeType.keySet.asScala.toSet

        val lines = tds.getTabularType.getIndexNames.asScala.toList match
          // Optimize tables with single key
          case uniqueKey :: Nil =>
            val humanizedMap = tds.values.asScala.toList.collect { case value: CompositeData =>
              val strKey = JKey(value.get(uniqueKey)).toString
              val rest = (keys - uniqueKey).toList
              rest match
                case singleKey :: Nil => strKey -> value.get(singleKey)
                case _                => strKey -> value.getAll(rest.toArray)
            }
            humanizedMap.sortBy(_._1).map { case (key, value: AnyRef) =>
              s"${key}: ${JValue(value)}"
            }

          case multipleKeys =>
            tds.asScala.toList.map { case (_, value) =>
              JValue(value).toString
            }
        indentLines(2)(newline + lines.mkString(newline))

      case other => other.toString

  case class JKey(value: AnyRef):
    override def toString = value match
      case compositeKey: CompositeData =>
        val keys = compositeKey.getCompositeType.keySet.asScala.toSeq.sorted
        val keysAndValues = keys.map(k => s"$k: ${compositeKey.get(k)}")
        val typeName = compositeKey.getCompositeType.getTypeName

        if typeName.endsWith("MXBean") then
          val shortName = typeName.split("\\.").last.replace("MXBean", "")
          s"$shortName(${keysAndValues.mkString(", ")})"
        else s"$typeName(${keysAndValues.mkString(", ")})"

      case other => other.toString

  case class JAttribute(a: Attribute):
    override def toString =
      "%s: %s".format(a.getName, JValue(a.getValue))

  def typeToClass(cl: ClassLoader)(t: String): Option[Class[?]] =
    t match
      case "boolean" => Some(classOf[java.lang.Boolean])
      case "byte"    => Some(classOf[java.lang.Byte])
      case "char"    => Some(classOf[java.lang.Character])
      case "short"   => Some(classOf[java.lang.Short])
      case "int"     => Some(classOf[java.lang.Integer])
      case "long"    => Some(classOf[java.lang.Long])
      case "float"   => Some(classOf[java.lang.Float])
      case "double"  => Some(classOf[java.lang.Double])
      case other =>
        try Some(Class.forName(t, true, cl))
        catch case e: ClassNotFoundException => None

  @tailrec final def extractValue(value: AnyRef, names: Seq[String]): Option[AnyRef] =
    if names.isEmpty then Some(value)
    else
      value match
        case cd: CompositeData =>
          val nextName = names.head
          Option(cd.get(nextName)) match
            case Some(nv) => extractValue(nv, names.tail)
            case None     => None
        case _ => None

  private val newline = "%n".format()
  private def indentLines(indent: Int)(s: String): String =
    s.split(newline).map(s => (" " * indent) + s).mkString(newline)
