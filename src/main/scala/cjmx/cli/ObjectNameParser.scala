package cjmx.cli


import sbt.complete.Parser
import sbt.complete.DefaultParsers._

import scalaz._
import Scalaz._

import com.sun.tools.attach._
import scala.collection.JavaConverters._

import javax.management._
import javax.management.remote.JMXConnector


object ObjectNameParser {
  import Parser._

  val JmxObjectName =
    (svr: MBeanServerConnection) => for {
      domain <- token(JmxObjectNameDomain(svr).? <~ ':') map { _ getOrElse "" }
      builder <- Properties(svr, domain)
      oname <- builder.oname.fold(n => Parser.success(n), Parser.failure("invalid object name"))
    } yield oname

  val JmxObjectNameDomain =
    (svr: MBeanServerConnection) => (charClass(_ != ':', "object name domain")+).string.examples(svr.getDomains: _*)

  private val Properties =
    (svr: MBeanServerConnection, domain: String) => {
      def recurse(soFar: ObjectNameBuilder): Parser[ObjectNameBuilder] = ((',' ~> Property(svr, soFar))?).flatMap { more =>
        more match {
          case Some(more) => recurse(more)
          case None => Parser.success(soFar)
        }
      }
      Property(svr, ObjectNameBuilder(domain)) flatMap recurse
    }

  private val Property =
    (svr: MBeanServerConnection, soFar: ObjectNameBuilder) =>
      (token("*") ^^^ soFar.addPropertyWildcardChar) |
      PropertyKeyValue(svr, soFar)

  private val PropertyKeyValue =
    (svr: MBeanServerConnection, soFar: ObjectNameBuilder) =>
      for {
        key <- token(PropertyKey(svr, soFar) <~ '=')
        value <- token("*" | PropertyValue(svr, soFar, key))
      } yield soFar.addProperty(key, value)

  private val PropertyKey =
    (svr: MBeanServerConnection, soFar: ObjectNameBuilder) => PropertyPart(valuePart = false).examples {
      val keys = for {
        nameSoFar <- soFar.addPropertyWildcardChar.oname.toSet
        name <- svr.queryNames(nameSoFar, null).asScala
        (key, value) <- name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap
        if !soFar.properties.contains(key)
      } yield key
      if (keys.nonEmpty)
        keys.toSet
      else
        Set("property")
    }

  private val PropertyValue =
    (svr: MBeanServerConnection, soFar: ObjectNameBuilder, key: String) =>
      PropertyPart(valuePart = true).examples {
        val values = for {
          nameSoFar <- soFar.addProperty(key, "*").addPropertyWildcardChar.oname.toSet
          name <- svr.queryNames(nameSoFar, null).asScala
          value <- (name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap).get(key)
        } yield value
        values.toSet
      }

  private def PropertyPart(valuePart: Boolean) = any.+.string

  private case class ObjectNameBuilder(domain: String, properties: Map[String, String] = Map.empty, wildcardProperty: Boolean = false) {
    def addProperty(key: String, value: String) = copy(properties = properties + (key -> value))
    def addPropertyWildcardChar = copy(wildcardProperty = true)

    override def toString = domain + ":" + (
      properties.map { case (k, v) => k + "=" + v } ++ (if (wildcardProperty) Seq("*") else Seq.empty)
    ).mkString(",")

    def oname: Option[ObjectName] = {
      try new Some(new ObjectName(toString))
      catch {
        case e: MalformedObjectNameException => None
      }
    }
  }
}
