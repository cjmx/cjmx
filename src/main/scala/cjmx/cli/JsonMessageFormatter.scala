package cjmx.cli

import scala.collection.JavaConverters._
import scala.collection.mutable.LinkedHashMap

import java.lang.reflect.Type
import java.text.DateFormat
import java.util.{List => JList}
import javax.management.{ObjectName, Attribute, MBeanInfo}
import javax.management.openmbean._

import com.google.gson._


object JsonMessageFormatter extends MessageFormatter {

  private val gson = new GsonBuilder().
    registerTypeAdapter(classOf[ObjectName], ObjectNameSerializer).
    registerTypeAdapter(classOf[NameAndAttributeValues], NameAndAttributeValuesSerializer).
    registerTypeAdapter(classOf[Attribute], AttributeSerializer).
    registerTypeHierarchyAdapter(classOf[CompositeData], CompositeDataSerializer).
    registerTypeHierarchyAdapter(classOf[InvocationResult], InvocationResultSerializer).
    serializeNulls.
    setDateFormat(DateFormat.LONG).
    setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).
    setPrettyPrinting.
    disableHtmlEscaping.
    create

  private def toJson(a: AnyRef): List[String] = List(gson.toJson(a))

  private def toLinkedHashMap[A, B](kvs: Traversable[(A, B)]) =
    (new LinkedHashMap[A, B] ++ kvs).asJava

  override def formatNames(names: Seq[ObjectName]) = {
    toJson(names.asJava)
  }

  override def formatAttributes(attrsByName: Seq[(ObjectName, Seq[Attribute])]) = {
    toJson(toLinkedHashMap(attrsByName.map { case (n, a) => n -> a.asJava }))
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

  private case class NameAndAttributeValues(name: ObjectName, attributes: List[Attribute])
  private object NameAndAttributeValuesSerializer extends JsonSerializer[NameAndAttributeValues] {
    override def serialize(src: NameAndAttributeValues, typeOfSrc: Type, context: JsonSerializationContext) = {
      val obj = new JsonObject
      obj.add("objectName", context.serialize(src.name))
      obj.add("attributes", context.serialize(src.attributes.asJava))
      obj
    }
  }

  private object AttributeSerializer extends JsonSerializer[Attribute] {
    override def serialize(src: Attribute, typeOfSrc: Type, context: JsonSerializationContext) = {
      val obj = new JsonObject
      obj.addProperty("name", src.getName)
      obj.add("value", context.serialize(src.getValue))
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
        case InvocationResults.Succeeded(value) => (true, value)
        case other => (false, other.toString)
      }
      obj.addProperty("successful", successful)
      obj.add("result", context.serialize(result))
      obj
    }
  }
}

