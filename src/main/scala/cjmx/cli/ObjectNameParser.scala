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
      keys.toSet + "<key>"
    }

  private val PropertyValue =
    (svr: MBeanServerConnection, soFar: ObjectNameBuilder, key: String) =>
      PropertyPart(valuePart = true).examples {
        val values = for {
          nameSoFar <- soFar.addProperty(key, "*").addPropertyWildcardChar.oname.toOption.toSet
          name <- svr.queryNames(nameSoFar, null).asScala
          value <- (name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap).get(key)
        } yield value
        values.toSet + "<value>"
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
