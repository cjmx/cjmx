package cjmx.cli

import scala.annotation.tailrec

import sbt._
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

  private def repFlatMap[A, B](init: A)(p: A => Parser[(A, B)]): Parser[Seq[B]] = {
    def repFlatMapR[A, B](last: A, acc: Seq[B])(p: A => Parser[(A, B)]): Parser[Seq[B]] = {
      p(last).?.flatMap { more => more match {
        case Some((s, b)) => repFlatMapR(s, b +: acc)(p)
        case None => Parser.success(acc.reverse)
      } }
    }
    repFlatMapR[A, B](init, Seq.empty)(p)
  }



  private val Property =
    (svr: MBeanServerConnection, soFar: ObjectNameBuilder) =>
      (token("*") ^^^ soFar.addPropertyWildcardChar) |
      PropertyKeyValue(svr, soFar) // TODO support quoting/escaping

  private val PropertyKeyValue =
    (svr: MBeanServerConnection, soFar: ObjectNameBuilder) =>
      for {
        key <- token(PropertyKey(svr, soFar) <~ '=')
        value <- token("*" | PropertyValue(svr, soFar, key))
      } yield soFar.addProperty(key, value)

  private val PropertyKey =
    (svr: MBeanServerConnection, soFar: ObjectNameBuilder) => PropertyPart(allowUnescapedWildcardChars = false).examples {
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
      PropertyPart(allowUnescapedWildcardChars = true).examples(examplePropertyValues(svr, soFar, key))

  private def examplePropertyValues(svr: MBeanServerConnection, soFar: ObjectNameBuilder, key: String): Set[String] = {
    val values = for {
      nameSoFar <- soFar.addProperty(key, "*").addPropertyWildcardChar.oname.toSet
      name <- svr.queryNames(nameSoFar, null).asScala
      value <- (name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap).get(key)
    } yield value
    values.toSet
  }

  private def PropertyPart(allowUnescapedWildcardChars: Boolean) =
    PropertyPartNonQuoted(allowUnescapedWildcardChars) | PropertyPartQuoted(allowUnescapedWildcardChars)

  private def PropertyPartNonQuoted(allowUnescapedWildcardChars: Boolean) =
    repFlatMap(none[Char]) { lastChar =>
      val p = {
        if (lastChar == Some('\\'))
          WildcardChar
        else if (allowUnescapedWildcardChars)
          PropertyChar | WildcardChar
        else
          PropertyChar
      }
      p.map { d => (Some(d), d) }
    }.string

  private def PropertyPartQuoted(allowUnescapedWildcardChars: Boolean) =
    (DQuoteChar ~> (repFlatMap(none[Char]) { lastChar =>
      val p = {
        if (lastChar == Some('\\'))
          WildcardChar | DQuoteChar
        else if (allowUnescapedWildcardChars)
          PropertyChar | WildcardChar
        else
          PropertyChar
      }
      p.map { d => (Some(d), d) }
    }.string) <~ DQuoteChar) map { s => """"%s"""".format(s) }

  private lazy val PropertyChar = charClass(c => !PropertyReservedChars.contains(c), "object name property")
  private lazy val WildcardChar = charClass(c => c == '*' || c == '?', "wildcard").examples(Set("*", "?"))
  private lazy val PropertyReservedChars = Set(':', '"', ',', '=', '*', '?')


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
