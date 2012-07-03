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
      builder <- Properties(svr, ObjectNameBuilder(domain))
      oname <- builder.oname.fold(e => Parser.failure("invalid object name: " + e), n => Parser.success(n))
    } yield oname

  val JmxObjectNameDomain =
    (svr: MBeanServerConnection) => (charClass(_ != ':', "object name domain")+).string.examples(svr.getDomains: _*)

  private def Properties(svr: MBeanServerConnection, soFar: ObjectNameBuilder): Parser[ObjectNameBuilder] =
    Property(svr, soFar) flatMap { p1 => (EOF ^^^ p1) | (',' ~> Properties(svr, p1)) }

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
        nameSoFar <- soFar.addPropertyWildcardChar.oname.toOption.toSet
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
          nameSoFar <- soFar.addProperty(key, "*").addPropertyWildcardChar.oname.toOption.toSet
          name <- svr.queryNames(nameSoFar, null).asScala
          value <- (name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap).get(key)
        } yield value
        values.toSet
      }

  private def PropertyPart(valuePart: Boolean) = any.+.string

  private case class ObjectNameBuilder(domain: String, properties: Map[String, String] = Map.empty, wildcardProperty: Boolean = false) {
    def addProperty(key: String, value: String) = copy(properties = properties + (key -> value))
    def addProperties(props: Map[String, String]) = copy(properties = properties ++ props)
    def addPropertyWildcardChar = copy(wildcardProperty = true)

    override def toString = domain + ":" + (
      properties.map { case (k, v) => k + "=" + v } ++ (if (wildcardProperty) Seq("*") else Seq.empty)
    ).mkString(",")

    def oname: Validation[MalformedObjectNameException, ObjectName] = {
      try new ObjectName(toString).success
      catch {
        case e: MalformedObjectNameException => e.fail
      }
    }
  }
}
