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
      PropertyPart(valuePart = true).examples(examplePropertyValues(svr, soFar, key))

  private def examplePropertyValues(svr: MBeanServerConnection, soFar: ObjectNameBuilder, key: String): Set[String] = {
    val values = for {
      nameSoFar <- soFar.addProperty(key, "*").addPropertyWildcardChar.oname.toSet
      name <- svr.queryNames(nameSoFar, null).asScala
      value <- (name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap).get(key)
    } yield value
    values.toSet
  }

  private def PropertyPart(valuePart: Boolean) =
    PropertyPartNonQuoted(valuePart) | PropertyPartQuoted(valuePart)

  private def PropertyPartNonQuoted(valuePart: Boolean) =
    repFlatMap(none[Char]) { lastChar =>
      val p = {
        if (lastChar == Some('\\'))
          WildcardChar
        else if (valuePart)
          PropertyChar | WildcardChar
        else
          PropertyChar
      }
      p.map { d => (Some(d), d) }
    }.string

  private def PropertyPartQuoted(valuePart: Boolean) =
    (DQuoteChar ~> (repFlatMap(none[Char]) { lastChar =>
      val p = {
        if (lastChar == Some('\\'))
          WildcardChar | DQuoteChar
        else if (valuePart)
          PropertyChar | ',' | WildcardChar
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

  /**
   * Creates a `Parser[Seq[B]]` from a `A => Parser[(A, B)]`.
   *
   * This allows parsing of a `Seq[B]` where the parser for each `B` is dependent upon some previous parser state.
   * The parser used in each step produces a tuple whose first parameter is the next state and whose second
   * parameter is the parsed value.
   *
   * Note: this method is recursive but not tail recursive.
   */
  private def repFlatMap[S, A](init: S)(p: S => Parser[(S, A)]): Parser[Seq[A]] = {
    def repFlatMapR[S, A](last: S, acc: Seq[A])(p: S => Parser[(S, A)]): Parser[Seq[A]] = {
      p(last).?.flatMap { more => more match {
        case Some((s, a)) => repFlatMapR(s, a +: acc)(p)
        case None => Parser.success(acc.reverse)
      } }
    }
    repFlatMapR[S, A](init, Seq.empty)(p)
  }
}
