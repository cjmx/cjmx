package cjmx.cli

import sbt._
import sbt.complete.Parser
import sbt.complete.DefaultParsers._

import scalaz._
import Scalaz._

import com.sun.tools.attach._
import scala.collection.JavaConverters._

import javax.management._
import javax.management.remote.JMXConnector


object Parsers {
  import Parser._

  private val ws = charClass(_.isWhitespace)+

  private val list: Parser[Action] =
    (token("list") | token("jps")) ^^^ Actions.ListVMs

  private val vmid: Seq[VirtualMachineDescriptor] => Parser[String] =
    (vms: Seq[VirtualMachineDescriptor]) => token(charClass(_.isDigit, "virtual machine id")+).string.examples(vms.map { _.id.toString }: _*)

  private val connect: Seq[VirtualMachineDescriptor] => Parser[Actions.Connect] =
    (vms: Seq[VirtualMachineDescriptor]) => (token("connect" ~ ' ') ~> vmid(vms)) map Actions.Connect.apply

  private val quit: Parser[Action] = (token("exit", _ => true) | token("done", _ => true) | token("quit")) ^^^ Actions.Quit

  private val globalActions = quit

  val disconnected =
    (vms: Seq[VirtualMachineDescriptor]) => list | connect(vms) | globalActions !!! "Invalid input"

  val query =
    (cnx: JMXConnector) => (token("query" ~ ' ') ~> queryString(cnx)) map Actions.Query

  val queryString =
    (cnx: JMXConnector) => jmxObjectName(cnx)

  val jmxObjectName =
    (cnx: JMXConnector) => for {
      domain <- token(jmxObjectNameDomain(cnx) <~ ':')
      builder <- jmxObjectNameProperties(cnx, domain)
    } yield builder.oname

  val jmxObjectNameDomain =
    (cnx: JMXConnector) => (charClass(_ != ':', "object name domain")+).string.examples(cnx.getMBeanServerConnection.getDomains: _*)


  private val jmxObjectNamePropertyReservedChars = Set(':', '"', ',', '=', '*', '?')
  private val jmxObjectNamePropertyNonQuoted =
    (charClass(c => !jmxObjectNamePropertyReservedChars.contains(c), "object name property")+).string

  private case class ObjectNameBuilder(domain: String, properties: Map[String, String] = Map.empty, wildcardProperty: Boolean = false) {
    def addProperty(key: String, value: String) = copy(properties = properties + (key -> value))
    def addPropertyWildcard = copy(wildcardProperty = true)

    override def toString = domain + ":" + (
      properties.map { case (k, v) => k + "=" + v } ++ (if (wildcardProperty) Seq("*") else Seq.empty)
    ).mkString(",")

    def oname = new ObjectName(toString)
  }

  private val jmxObjectNamePropertyKey =
    (cnx: JMXConnector, soFar: ObjectNameBuilder) => jmxObjectNamePropertyNonQuoted.examples {
      val keys = for {
        name <- cnx.getMBeanServerConnection.queryNames(soFar.addPropertyWildcard.oname, null).asScala
        (key, value) <- name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap
        if !soFar.properties.contains(key)
      } yield key
      if (keys.nonEmpty)
        keys.toSet
      else
        Set("property")
    }

  private val jmxObjectNamePropertyValue =
    (cnx: JMXConnector, soFar: ObjectNameBuilder, key: String) => jmxObjectNamePropertyNonQuoted.examples {
      val values = for {
        name <- cnx.getMBeanServerConnection.queryNames(soFar.addProperty(key, "*").addPropertyWildcard.oname, null).asScala
        value <- (name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap).get(key)
      } yield value
      values.toSet
    }

  private val jmxObjectNamePropertyKeyValue =
    (cnx: JMXConnector, soFar: ObjectNameBuilder) =>
      for {
        key <- token(jmxObjectNamePropertyKey(cnx, soFar) <~ '=')
        value <- token("*" | jmxObjectNamePropertyValue(cnx, soFar, key))
      } yield soFar.addProperty(key, value)

  private val jmxObjectNameProperty =
    (cnx: JMXConnector, soFar: ObjectNameBuilder) =>
      (token("*") ^^^ soFar.addPropertyWildcard) |
      jmxObjectNamePropertyKeyValue(cnx, soFar) // TODO support quoting/escaping


  private val jmxObjectNameProperties =
    (cnx: JMXConnector, domain: String) => {
      def recurse(soFar: ObjectNameBuilder): Parser[ObjectNameBuilder] = ((',' ~> jmxObjectNameProperty(cnx, soFar))?).flatMap { more =>
        more match {
          case Some(more) => recurse(more)
          case None => Parser.success(soFar)
        }
      }
      jmxObjectNameProperty(cnx, ObjectNameBuilder(domain)) flatMap recurse
    }
//      rep1sep(jmxObjectNameProperty(cnx, domain), ',') map { _.mkString(",") }

  val names =
    (cnx: JMXConnector) => token("names" ~ ' ') ~> jmxObjectName(cnx) map { name => Actions.ManagedObjectNames(Some(name), None) }

  val disconnect = token("disconnect") ^^^ Actions.Disconnect

  val connected =
    (cnx: JMXConnector) => names(cnx) | query(cnx) | disconnect | globalActions !!! "Invalid input"
}


