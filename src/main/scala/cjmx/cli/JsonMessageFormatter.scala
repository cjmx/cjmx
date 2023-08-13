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

package cjmx.cli

import scala.collection.immutable.Seq
import scala.collection.JavaConverters._
import scala.collection.mutable.LinkedHashMap

import java.lang.reflect.Type
import java.text.DateFormat
import javax.management.{ObjectName, Attribute, MBeanInfo}
import javax.management.openmbean._

import cjmx.util.jmx.JMX
import com.google.gson._

object JsonMessageFormatter {

  val standard: JsonMessageFormatter = new JsonMessageFormatter {
    private object TabularDataSupportSerializer extends JsonSerializer[TabularDataSupport] {
      override def serialize(src: TabularDataSupport, typeOfSrc: Type, context: JsonSerializationContext) = {
        val arr: JsonArray = new JsonArray()
        src.asScala foreach { case (_, value) =>
          arr.add(context.serialize(value))
        }
        arr
      }
    }

    val gson = gsonBuilder.
      registerTypeHierarchyAdapter(classOf[TabularDataSupport], TabularDataSupportSerializer).
      create
  }

  val compact: JsonMessageFormatter = new JsonMessageFormatter {
    private object CompactTabularDataSupportSerializer extends JsonSerializer[TabularDataSupport] {
      override def serialize(src: TabularDataSupport, typeOfSrc: Type, context: JsonSerializationContext) = {
        val tabularType = src.getTabularType
        val compositeType = tabularType.getRowType
        val keys = compositeType.keySet.asScala

        src.getTabularType.getIndexNames.asScala.toList match {
          // Optimize JSON for tables with single key
          case uniqueKey :: Nil =>
            val obj: JsonObject = new JsonObject()
            val entries = src.values.asScala.toList collect { case value: CompositeData =>
              val rest = (keys - uniqueKey).toList
              val indexKey = JMX.JKey(value.get(uniqueKey)).toString

              rest match  {
                case singleKey :: Nil =>
                  indexKey -> context.serialize(value.get(singleKey))
                case _ =>
                  indexKey -> context.serialize(value.getAll(rest.toArray))
              }
            }
            entries.sortBy { _._1 }.foreach { case (key, value) =>
              obj.add(key, value)
            }
            obj

          case multipleKeys =>
            val arr: JsonArray = new JsonArray()
            src.asScala foreach { case (_, value) =>
              arr.add(context.serialize(value))
            }
            arr
        }
      }
    }


    val gson = gsonBuilder.
      registerTypeHierarchyAdapter(classOf[TabularDataSupport], CompactTabularDataSupportSerializer).
      create
  }
}

abstract class JsonMessageFormatter extends MessageFormatter {
  val gsonBuilder = new GsonBuilder().
    registerTypeAdapter(classOf[ObjectName], ObjectNameSerializer).
    registerTypeAdapter(classOf[Attributes], AttributesSerializer).
    registerTypeHierarchyAdapter(classOf[CompositeData], CompositeDataSerializer).
    registerTypeHierarchyAdapter(classOf[InvocationResult], InvocationResultSerializer).
    serializeNulls.
    setDateFormat(DateFormat.LONG).
    setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).
    setPrettyPrinting.
    disableHtmlEscaping

  def gson: Gson

  private def toJson(a: AnyRef): List[String] = List(gson.toJson(a))

  private def toLinkedHashMap[A, B](kvs: Traversable[(A, B)]) =
    (new LinkedHashMap[A, B] ++ kvs).asJava

  override def formatNames(names: Seq[ObjectName]) = {
    toJson(names.asJava)
  }

  override def formatAttributes(attrsByName: Seq[(ObjectName, Seq[Attribute])]) = {
    toJson(toLinkedHashMap(attrsByName.map { case (n, a) => n -> Attributes(a) }))
  }

  override def formatInfo(info: Seq[(ObjectName, MBeanInfo)], detailed: Boolean) = {
    toJson(toLinkedHashMap(info))
  }

  override def formatInvocationResults(namesAndResults: Seq[(ObjectName, InvocationResult)]) = {
    toJson(toLinkedHashMap(namesAndResults))
  }

  private object ObjectNameSerializer extends JsonSerializer[ObjectName] {
    override def serialize(src: ObjectName, typeOfSrc: Type, context: JsonSerializationContext) =
      new JsonPrimitive(src.toString)
  }

  private case class Attributes(attrs: Seq[Attribute])
  private object AttributesSerializer extends JsonSerializer[Attributes] {
    override def serialize(src: Attributes, typeOfSrc: Type, context: JsonSerializationContext) = {
      val obj = new JsonObject
      src.attrs foreach { attr =>
        attr.getValue match {
          case vd: java.lang.Double if vd.isNaN || vd.isInfinite => obj.add(attr.getName, context.serialize(vd.toString))
          case vf: java.lang.Float if vf.isNaN || vf.isInfinite => obj.add(attr.getName, context.serialize(vf.toString))
          case v => obj.add(attr.getName, context.serialize(v))
        }
      }
      obj
    }
  }

  private object CompositeDataSerializer extends JsonSerializer[CompositeData] {
    override def serialize(src: CompositeData, typeOfSrc: Type, context: JsonSerializationContext) = {
      val keys = src.getCompositeType.keySet.asScala.toSeq.sorted
      val keysAndValues = keys zip src.getAll(keys.toArray).toSeq
      context.serialize(toLinkedHashMap(keysAndValues))
    }
  }

  private object InvocationResultSerializer extends JsonSerializer[InvocationResult] {
    override def serialize(src: InvocationResult, typeOfSrc: Type, context: JsonSerializationContext) = {
      val obj = new JsonObject
      val (successful, result) = src match {
        case InvocationResult.Succeeded(value) => (true, value)
        case other => (false, other.toString)
      }
      obj.addProperty("successful", successful)
      obj.add("result", context.serialize(result))
      obj
    }
  }
}
