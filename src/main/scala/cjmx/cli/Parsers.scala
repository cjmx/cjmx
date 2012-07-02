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
      props <- jmxObjectNameProperties(cnx, domain)
    } yield new ObjectName(domain + ":" + props)

  val jmxObjectNameDomain =
    (cnx: JMXConnector) => (charClass(_ != ':', "object name domain")+).string.examples(cnx.getMBeanServerConnection.getDomains: _*)


  private val jmxObjectNamePropertyReservedChars = Set(':', '"', ',', '=', '*', '?')
  private val jmxObjectNamePropertyNonQuoted =
    (charClass(c => !jmxObjectNamePropertyReservedChars.contains(c), "object name property")+).string

  private val jmxObjectNamePropertyKey =
    (cnx: JMXConnector, domain: String) => jmxObjectNamePropertyNonQuoted.examples {
      val keys = for {
        name <- cnx.getMBeanServerConnection.queryNames(new ObjectName(domain + ":*"), null).asScala
        (key, value) <- name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap
      } yield key
      keys.toSet
    }

  private val jmxObjectNamePropertyValue =
    (cnx: JMXConnector, domain: String, key: String) => jmxObjectNamePropertyNonQuoted.examples {
      val values = for {
        name <- cnx.getMBeanServerConnection.queryNames(new ObjectName("%s:*,%s=*".format(domain, key)), null).asScala
        (key, value) <- name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap
      } yield value
      values.toSet
    }

  private val jmxObjectNamePropertyKeyValue =
    (cnx: JMXConnector, domain: String) =>
      for {
        key <- token(jmxObjectNamePropertyKey(cnx, domain) <~ '=')
        value <- token("*" | jmxObjectNamePropertyValue(cnx, domain, key))
      } yield "%s=%s".format(key, value)

  private val jmxObjectNameProperty =
    (cnx: JMXConnector, domain: String) => token("*") | jmxObjectNamePropertyKeyValue(cnx, domain) // TODO support quoting/escaping


  private val jmxObjectNameProperties =
    (cnx: JMXConnector, domain: String) => rep1sep(jmxObjectNameProperty(cnx, domain), ',') map { _.mkString(",") }

  val names =
    (cnx: JMXConnector) => token("names" ~ ' ') ~> jmxObjectName(cnx) map { name => Actions.ManagedObjectNames(Some(name), None) }

  val disconnect = token("disconnect") ^^^ Actions.Disconnect

  val connected =
    (cnx: JMXConnector) => names(cnx) | query(cnx) | disconnect | globalActions !!! "Invalid input"
}


