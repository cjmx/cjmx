package cjmx.cli

import sbt.complete.Parser
import sbt.complete.DefaultParsers._

import scalaz.{Digit => _, _}
import Scalaz._

import com.sun.tools.attach._
import scala.collection.JavaConverters._

import javax.management._
import javax.management.remote.JMXConnector

import MoreParsers._

import cjmx.util.jmx.JMX


object JMXParsers {
  import Parser._

  val QuotedObjectNameParser =
    (svr: MBeanServerConnection) => token('\'') ~> ObjectNameParser(svr) <~ token('\'')

  val ObjectNameParser =
    (svr: MBeanServerConnection) => for {
      domain <- token(ObjectNameDomainParser(svr).? <~ ':') map { _ getOrElse "" }
      builder <- ObjectNameProductions.Properties(svr, ObjectNameBuilder(domain))
      oname <- builder.oname.fold(e => Parser.failure("invalid object name: " + e), n => Parser.success(n))
    } yield oname

  val ObjectNameDomainParser =
    (svr: MBeanServerConnection) => (charClass(_ != ':', "object name domain")+).string.examples(svr.getDomains: _*)

  private object ObjectNameProductions {

    def Properties(svr: MBeanServerConnection, soFar: ObjectNameBuilder): Parser[ObjectNameBuilder] =
      Property(svr, soFar) flatMap { p1 => (EOF ^^^ p1) | (',' ~> Properties(svr, p1)) }

    val Property =
      (svr: MBeanServerConnection, soFar: ObjectNameBuilder) =>
        (token("*") ^^^ soFar.addPropertyWildcardChar) |
        PropertyKeyValue(svr, soFar)

    val PropertyKeyValue =
      (svr: MBeanServerConnection, soFar: ObjectNameBuilder) =>
        for {
          key <- token(PropertyKey(svr, soFar) <~ '=')
          value <- token("*" | PropertyValue(svr, soFar, key))
        } yield soFar.addProperty(key, value)

    val PropertyKey =
      (svr: MBeanServerConnection, soFar: ObjectNameBuilder) => PropertyPart(valuePart = false).flatMap { key =>
        if (soFar.properties contains key)
          Parser.failure("duplicate key " + key)
        else
          success(key)
      }.examples {
        val keys = for {
          nameSoFar <- soFar.addPropertyWildcardChar.oname.toOption.toSet
          name <- svr.queryNames(nameSoFar, null).asScala
          (key, value) <- name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap
          if !soFar.properties.contains(key)
        } yield key
        keys.toSet + "<key>"
      }

    val PropertyValue =
      (svr: MBeanServerConnection, soFar: ObjectNameBuilder, key: String) =>
        PropertyPart(valuePart = true).examples {
          val values = for {
            nameSoFar <- soFar.addProperty(key, "*").addPropertyWildcardChar.oname.toOption.toSet
            name <- svr.queryNames(nameSoFar, null).asScala
            value <- (name.getKeyPropertyList |> collection.JavaConversions.mapAsScalaMap).get(key)
          } yield value
          values.toSet + "<value>"
        }

    def PropertyPart(valuePart: Boolean) =
      PropertyPartNonQuoted(valuePart) | PropertyPartQuoted(valuePart)

    def PropertyPartNonQuoted(valuePart: Boolean) =
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

    def PropertyPartQuoted(valuePart: Boolean) =
      (DQuoteChar ~> (repFlatMap(none[Char]) { lastChar =>
        val p = {
          if (lastChar == Some('\\'))
            WildcardChar | DQuoteChar
          else if (valuePart)
            QuotedValueChar
          else
            PropertyChar
        }
        p.map { d => (Some(d), d) }
      }.string) <~ DQuoteChar) map { s => """"%s"""".format(s) }

    lazy val PropertyChar = charClass(c => !PropertyReservedChars.contains(c), "object name property")
    lazy val WildcardChar = charClass(c => c == '*' || c == '?', "wildcard").examples(Set("*", "?"))
    lazy val QuotedValueChar = charClass(_ != '\"')
    lazy val PropertyReservedChars = PropertyReservedCharsNoDQuote + '\"'
    lazy val PropertyReservedCharsNoDQuote = Set(':', ',', '=', '*', '?')
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



  lazy val QueryExpParser: (MBeanServerConnection, ObjectName) => Parser[QueryExp] = (svr: MBeanServerConnection, name: ObjectName) => {
    val attributeNames = svr.toScala.queryNames(Some(name), None).flatMap { n =>
      svr.getMBeanInfo(n).getAttributes.map { _.getName }.toSet
    }
    new QueryExpProductions(attributeNames).Query
  }

  private class QueryExpProductions(attributeNames: Set[String]) {
    import javax.management.{Query => Q}

    lazy val Query: Parser[QueryExp] = { for {
      p <- (AndQuery <~ SpaceClass.*)
      q <- (token("or ") ~> SpaceClass.* ~> Query).?
    } yield q.fold(qq => Q.or(p, qq), p) }

    lazy val AndQuery: Parser[QueryExp] = for {
      p <- (Predicate <~ SpaceClass.*)
      q <- (token("and ") ~> SpaceClass.* ~> AndQuery).?
    } yield q.fold(qq => Q.and(p, qq), p)

    lazy val Not = token("not ") <~ SpaceClass.* ^^^ true

    lazy val Predicate: Parser[QueryExp] = {
      Parenthesized(Query).examples("(<predicate>)") |
      NotPredicate |
      InstanceOf |
      ValuePredicate
    }

    def Parenthesized[A](p: => Parser[A]) = for {
      _ <- token("(") <~ SpaceClass.*
      pp <- p <~ SpaceClass.* <~ token(")")
    } yield pp

    lazy val NotPredicate =
      (Not flatMap { _ => Predicate }).map(Q.not).examples("not <predicate>")

    lazy val InstanceOf =
      (token("instanceof ") ~> (token(SpaceClass.* ~> StringLiteral)).examples("'<type name>'")).map(Q.isInstanceOf)

    lazy val ValuePredicate =
      (token(Value) flatMap { lhs => SpaceClass.* ~> {
        val dependentOnOnlyValueExp =
          RelationalOperation(lhs) | (SpaceClass.+ ~> (Negatable(Between(lhs)) | Negatable(In(lhs))))
        lhs match {
          case av: AttributeValueExp =>
            dependentOnOnlyValueExp | (SpaceClass.+ ~> (Negatable(Match(av)) | Negatable(SubString(av))))
          case _ =>
            dependentOnOnlyValueExp
        }
      }})

    def RelationalOperation(lhs: ValueExp) = {
      def binaryOp(op: String, f: (ValueExp, ValueExp) => QueryExp): Parser[QueryExp] =
        token(op) ~> SpaceClass.* ~> Value map { rhs => f(lhs, rhs) }
      binaryOp("=", Q.eq) |
      binaryOp("<", Q.lt) |
      binaryOp(">", Q.gt) |
      binaryOp("<=", Q.leq) |
      binaryOp(">=", Q.geq) |
      binaryOp("!=", (lhs, rhs) => Q.not(Q.eq(lhs, rhs)))
    }

    def Negatable(p: Parser[QueryExp]): Parser[QueryExp] =
      Not.? ~ p map {
        case Some(true) ~ q => Q.not(q)
        case _ ~ q => q
      }

    def Between(lhs: ValueExp) = for {
      _ <- token("between ") <~ SpaceClass.*
      low <- Value <~ SpaceClass.* <~ token("and ") <~ SpaceClass.*
      hi <- Value
    } yield Q.between(lhs, low, hi)

    def In(lhs: ValueExp) = for {
      _ <- token("in ") <~ SpaceClass.* <~ token("(")
      values <- repsep(Value, SpaceClass.* ~ ',' ~ SpaceClass.*)
      _ <- SpaceClass.* ~ token(")")
    } yield Q.in(lhs, values.toArray)

    def Match(lhs: AttributeValueExp) = for {
      _ <- token("like ") <~ SpaceClass.*
      pat <- StringLiteral
    } yield Q.`match`(lhs, pat)

    def SubString(lhs: AttributeValueExp) =
      InitialSubString(lhs) | FinalSubString(lhs) | AnySubString(lhs)

    def InitialSubString(lhs: AttributeValueExp) = for {
      _ <- token("startsWith ") <~ SpaceClass.*
      ss <- StringLiteral
    } yield Q.initialSubString(lhs, ss)

    def FinalSubString(lhs: AttributeValueExp) = for {
      _ <- token("endsWith ") <~ SpaceClass.*
      ss <- StringLiteral
    } yield Q.finalSubString(lhs, ss)

    def AnySubString(lhs: AttributeValueExp) = for {
      _ <- token("contains ") <~ SpaceClass.*
      ss <- StringLiteral
    } yield Q.anySubString(lhs, ss)

    lazy val Value: Parser[ValueExp] = {
      for {
        lhs <- Sum
        opAndRhs <- ((SpaceClass.* ~> ('+'.id | '-') <~ SpaceClass.*) ~ Value).?
      } yield opAndRhs match {
        case Some('+' ~ rhs) => Q.plus(lhs, rhs)
        case Some('-' ~ rhs) => Q.minus(lhs, rhs)
        case None => lhs
      }
    }.examples(Set("<value>", "<attribute>") ++ attributeNames)

    lazy val Sum: Parser[ValueExp] = {
      for {
        lhs <- Product
        opAndRhs <- ((SpaceClass.* ~> ('*'.id | '/') <~ SpaceClass.*) ~ Value).?
      } yield opAndRhs match {
        case Some('*' ~ rhs) => Q.times(lhs, rhs)
        case Some('/' ~ rhs) => Q.div(lhs, rhs)
        case None => lhs
      }
    }

    lazy val Product: Parser[ValueExp] = Attribute | Literal | Parenthesized(Value).examples("(<value>)")
    lazy val Attribute = (NonQualifiedAttribute | QualifiedAttribute).examples("<attribute>")
    lazy val NonQualifiedAttribute = Identifier map Q.attr
    lazy val QualifiedAttribute = (rep1sep(JavaIdentifier, '.') <~ '#') ~ JavaIdentifier map { case clsParts ~ attr => Q.attr(clsParts.mkString("."), attr) }

    lazy val Literal = BooleanLiteral | LongLiteral | DoubleLiteral | StringLiteral
    lazy val BooleanLiteral = BooleanValue map Q.value
    lazy val LongLiteral = LongValue map Q.value
    lazy val DoubleLiteral = DoubleValue map Q.value
    lazy val StringLiteral = StringValue map Q.value
  }


  lazy val Projection: (MBeanServerConnection, ObjectName, Option[QueryExp]) => Parser[Seq[Attribute] => Seq[Attribute]] =
    (svr: MBeanServerConnection, name: ObjectName, query: Option[QueryExp]) => {
      (token("*") ^^^ (identity[Seq[Attribute]] _)) |
      (rep1sep(ProjectionAttributeRename(svr, name, query), SpaceClass.* ~ ',' ~ SpaceClass.*) map { attrMappings =>
        val lookup: Map[String, List[AnyRef => Option[Attribute]]] = attrMappings.map { case (k, v) => Map(k -> List(v)) }.toList.concatenate
        (attrs: Seq[Attribute]) => attrs.collect {
          case attr if lookup contains attr.getName => lookup(attr.getName).flatMap { f => f(attr.getValue) }
        }.flatten
      })
    }

  private lazy val ProjectionAttributeRename: (MBeanServerConnection, ObjectName, Option[QueryExp]) => Parser[(String, AnyRef => Option[Attribute])] =
    (svr: MBeanServerConnection, name: ObjectName, query: Option[QueryExp]) =>
      ProjectionAttributeInclusion(svr, name, query) ~ (token(" as ") ~> Identifier).? map {
        case (n, f) ~ Some(t) => (n, v => f(v).map { vv => new Attribute(t, vv.getValue) })
        case (n, f) ~ None => (n, f)
      }

  private lazy val ProjectionAttributeInclusion: (MBeanServerConnection, ObjectName, Option[QueryExp]) => Parser[(String, AnyRef => Option[Attribute])] =
    (svr: MBeanServerConnection, name: ObjectName, query: Option[QueryExp]) => {
      (rep1sep(Identifier, '.') map { ids =>
        (ids.head, v => JMX.extractValue(v, ids.tail) map { vv => new Attribute(ids.mkString("."), vv) })
      })
    }

  lazy val Invocation: MBeanServerConnection => Parser[(String, Seq[AnyRef])] =
    (svr: MBeanServerConnection) => {
      Identifier ~ (token("(") ~> repsep(SpaceClass.* ~> InvocationParameter(svr), SpaceClass.* ~ ',') <~ token(")"))
    }

  private lazy val InvocationParameter: MBeanServerConnection => Parser[AnyRef] =
    (svr: MBeanServerConnection) => {
      (BooleanValue map { v => (java.lang.Boolean.valueOf(v): AnyRef) }) |
      (IntValue map { v => (java.lang.Integer.valueOf(v): AnyRef) }) |
      (LongValue map { v => (java.lang.Long.valueOf(v): AnyRef) }) |
      (DoubleValue map { v => (java.lang.Double.valueOf(v): AnyRef) }) |
      (StringValue) |
      Arr(BooleanValue) |
      Arr(IntValue) |
      Arr(LongValue) |
      Arr(DoubleValue) |
      Arr(StringValue)
    }

  private def Arr[A: ClassManifest](p: Parser[A]): Parser[Array[A]] =
    (token("{") ~> repsep(SpaceClass.* ~> p, SpaceClass.* ~> ',') <~ SpaceClass.* <~ token("}")) map { _.toArray }


  private lazy val DottedIdentifier = rep1sep(Identifier, '.')
  private lazy val Identifier = (JavaIdentifier | QuotedIdentifier).examples("identifier")

  private lazy val JavaIdentifier: Parser[String] = (
    charClass(c => c.isLetter || c == '_' | c == '$', "letter, underscore, or dollar sign") ~
    charClass(c => c.isLetter || c == '_' | c == '$' || c.isDigit, "letter, digit, underscore, or dollar sign").* map {
      case x ~ xs => (x +: xs).mkString
    }
  ).examples("identifier")

  private lazy val QuotedIdentifier: Parser[String] =
    (DQuoteChar ~> (charClass(_ != DQuoteChar) | (DQuoteChar ~ DQuoteChar ^^^ DQuoteChar)).* <~ DQuoteChar).string.examples("\"identifier\"")


  private lazy val BooleanValue = ("true" ^^^ true) | ("false" ^^^ false)

  private lazy val IntValue = mapOrFail('-'.? ~ Digit.+) {
    case neg ~ digits => (neg.toSeq ++ digits).mkString.toInt
  } examples ("<number>")

  private lazy val LongValue = mapOrFail('-'.? ~ Digit.+ <~ ('L'.id | 'l').?) {
    case neg ~ digits => (neg.toSeq ++ digits).mkString.toLong
  } examples ("<number>")

  private lazy val DoubleValue = mapOrFail('-'.? ~ Digit.+ ~ ('.'.? ~> Digit.+.?)) {
    case neg ~ digits ~ mantissaDigits =>
      (neg.toSeq ++ digits ++ "." ++ mantissaDigits.getOrElse(Seq.empty)).mkString.toDouble
  } examples("<number>")

  private lazy val StringValue = '\'' ~> (charClass(_ != '\'') | ('\\' ~> '\'')).*.string <~ '\'' examples("'<string>'")
}
