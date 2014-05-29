package cjmx.cli

import scala.collection.JavaConverters._
import scala.collection.JavaConversions.mapAsScalaMap

import sbt.complete.Parser
import sbt.complete.DefaultParsers._

import scalaz.{Digit => _, _}
import scalaz.std.option._
import scalaz.syntax.apply._
import scalaz.syntax.id._
import scalaz.syntax.std.option._

import com.sun.tools.attach._

import javax.management._

import MoreParsers._

import cjmx.ext.AttributePathValueExp
import cjmx.util.Math.liftToBigDecimal
import cjmx.util.jmx.{JMX, MBeanQuery}
import cjmx.util.jmx.JMX._
import cjmx.util.jmx.Beans.{Field,Projection,Results,SubqueryName,unnamed}
import cjmx.util.jmx.Beans


object JMXParsers {
  import Parser._

  def QuotedObjectNameParser(svr: MBeanServerConnection): Parser[Map[SubqueryName,ObjectName]] =
    token('\'') ~> ObjectNameParser(svr) <~ token('\'')

  // todo: generalize this
  def ObjectNameParser(svr: MBeanServerConnection): Parser[Map[SubqueryName,ObjectName]] =
    for {
      domain <- token(ObjectNameDomainParser(svr).? <~ ':') map { _ getOrElse "" }
      builder <- ObjectNameProductions.Properties(svr, ObjectNameBuilder(domain))
      oname <- builder.oname.fold(e => Parser.failure("invalid object name: " + e), n => Parser.success(n))
    } yield Map(unnamed -> oname)

  def ObjectNameDomainParser(svr: MBeanServerConnection): Parser[String] =
    (charClass(_ != ':', "object name domain").+).string.examples(svr.getDomains: _*)

  private object ObjectNameProductions {

    def Properties(svr: MBeanServerConnection, soFar: ObjectNameBuilder): Parser[ObjectNameBuilder] =
      Property(svr, soFar) flatMap { p1 => (EOF ^^^ p1) | (',' ~> Properties(svr, p1)) }

    def Property(svr: MBeanServerConnection, soFar: ObjectNameBuilder): Parser[ObjectNameBuilder] =
      (token("*") ^^^ soFar.addPropertyWildcardChar) |
      PropertyKeyValue(svr, soFar)

    def PropertyKeyValue(svr: MBeanServerConnection, soFar: ObjectNameBuilder): Parser[ObjectNameBuilder] =
      for {
        key <- token(PropertyKey(svr, soFar) <~ '=')
        value <- token("*" | PropertyValue(svr, soFar, key))
      } yield soFar.addProperty(key, value)

    def PropertyKey(svr: MBeanServerConnection, soFar: ObjectNameBuilder): Parser[String] =
      PropertyPart(valuePart = false).flatMap { key =>
        if (soFar.properties contains key)
          Parser.failure("duplicate key " + key)
        else
          success(key)
      }.examples {
        val keys = for {
          nameSoFar <- soFar.addPropertyWildcardChar.oname.toOption.toSet: Set[ObjectName]
          name <- safely(Set.empty[ObjectName]) { svr.toScala.queryNames(Some(nameSoFar), None) }
          (key, value) <- name.getKeyPropertyList |> mapAsScalaMap
          if !soFar.properties.contains(key)
        } yield key
        keys.toSet + "<key>"
      }

    def PropertyValue(svr: MBeanServerConnection, soFar: ObjectNameBuilder, key: String): Parser[String] =
      PropertyPart(valuePart = true).examples {
        val values = for {
          nameSoFar <- soFar.addProperty(key, "*").addPropertyWildcardChar.oname.toOption.toSet: Set[ObjectName]
          name <- safely(Set.empty[ObjectName]) { svr.toScala.queryNames(Some(nameSoFar), None) }
          value <- (name.getKeyPropertyList |> mapAsScalaMap).get(key)
        } yield value
        values.toSet + "<value>"
      }

    def PropertyPart(valuePart: Boolean): Parser[String] =
      PropertyPartNonQuoted(valuePart) | PropertyPartQuoted(valuePart)

    def PropertyPartNonQuoted(valuePart: Boolean): Parser[String] =
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

    def PropertyPartQuoted(valuePart: Boolean): Parser[String] =
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

    def oname: MalformedObjectNameException \/ ObjectName = {
      try new ObjectName(toString).right
      catch {
        case e: MalformedObjectNameException => e.left
      }
    }
  }


  def QueryExpParser(results: Results): Parser[Field[Boolean]] =
    new QueryExpProductions(results).Query

  private class QueryExpProductions(results: Results) {

    val attributeNames: Set[String] = results.qualifiedNames

    import javax.management.{Query => Q}

    lazy val Query: Parser[Field[Boolean]] =
      (AndQuery ~ (
        (ws.* ~> token("or ") ~> ws.* ~> AndQuery <~ ws.*) map { q2 => (q1: Field[Boolean]) => q1 || q2 }
      ).*) map { case p ~ seq => seq.foldLeft(p) { (acc, q) => q(acc) } }

    lazy val AndQuery: Parser[Field[Boolean]] =
      (Predicate ~ (
        (ws.* ~> token("and ") ~> ws.* ~> Predicate <~ ws.*) map { q2 => (q1: Field[Boolean]) => q1 || q2 }
      ).*) map { case p ~ seq => seq.foldLeft(p) { (acc, q) => q(acc) } }

    lazy val Not = token("not ") <~ ws.* ^^^ true

    lazy val Predicate: Parser[Field[Boolean]] = {
      Parenthesized(Query).examples("(<predicate>)") |
      NotPredicate |
      InstanceOf |
      ValuePredicate
    }

    lazy val NotPredicate =
      (Not flatMap { _ => Predicate }).map(_.not).examples("not <predicate>")

    lazy val Subquery: Parser[SubqueryName] =
      ???

    lazy val InstanceOf =
      (Subquery.? ~ (token("instanceof ") ~> (token(ws.* ~> StringValue)).examples("'<type name>'"))).map {
        case sub ~ classname => Field.classnameEquals(sub.getOrElse(unnamed), classname)
      }

    lazy val ValuePredicate =
      (token(Value) flatMap { lhs => ws.* ~> {
        RelationalOperation(lhs) | (ws.+ ~> (Negatable(Between(lhs)) | Negatable(In(lhs))))
      }})

    def RelationalOperation(lhs: Field[Any]) = {
      def binaryOp(op: String, f: (Field[Any], Field[Any]) => Field[Boolean]): Parser[Field[Boolean]] =
        token(op) ~> ws.* ~> Value map { rhs => f(lhs, rhs) }
      binaryOp("=", _ === _) |
      binaryOp("<", (a,b) => a.asNumber < b.asNumber) |
      binaryOp(">", (a,b) => a.asNumber > b.asNumber) |
      binaryOp("<=", (a,b) => a.asNumber <= b.asNumber) |
      binaryOp(">=", (a,b) => a.asNumber >= b.asNumber) |
      binaryOp("!=", (a,b) => a !== b)
    }

    def Negatable(p: Parser[Field[Boolean]]): Parser[Field[Boolean]] =
      Not.? ~ p map {
        case Some(true) ~ q => q.not
        case _ ~ q => q
      }

    def Between(lhs: Field[Any]) = for {
      _ <- token("between ") <~ ws.*
      low <- Value <~ ws.* <~ token("and ") <~ ws.*
      hi <- Value
    } yield lhs.asNumber.between(low.asNumber, hi.asNumber)

    def In(lhs: Field[Any]) = for {
      _ <- token("in ") <~ ws.* <~ token("(")
      values <- repsep(Value, ws.* ~ ',' ~ ws.*)
      _ <- ws.* ~ token(")")
    } yield lhs.in(values: _*)

    def Match(lhs: Field[Any]) = for {
      _ <- token("like ") <~ ws.*
      pat <- StringLiteral
    } yield lhs.as[String].like(pat)

    def SubString(lhs: Field[Any]) =
      InitialSubString(lhs) | FinalSubString(lhs) | AnySubString(lhs)

    def InitialSubString(lhs: Field[Any]) = for {
      _ <- token("startsWith ") <~ ws.*
      ss <- StringLiteral
    } yield lhs.as[String].startsWith(ss)

    def FinalSubString(lhs: Field[Any]) = for {
      _ <- token("endsWith ") <~ ws.*
      ss <- StringLiteral
    } yield lhs.as[String].endsWith(ss)

    def AnySubString(lhs: Field[Any]) = for {
      _ <- token("contains ") <~ ws.*
      ss <- StringLiteral
    } yield ss.isSubstringOf(lhs.as[String])

    lazy val Value: Parser[Field[Any]] = new ExpressionParser {
      override type Expression = Field[Any]
      override def multiply(lhs: Expression, rhs: Expression) = lhs.asNumber * rhs.asNumber
      override def divide(lhs: Expression, rhs: Expression) = lhs.asNumber / rhs.asNumber
      override def add(lhs: Expression, rhs: Expression) = lhs.asNumber + rhs.asNumber
      override def subtract(lhs: Expression, rhs: Expression) = lhs.asNumber - rhs.asNumber
      override lazy val Value = Attribute | Literal
    }.Expr.examples(Set("<value>", "<attribute>") ++ attributeNames)

    lazy val Attribute = (NonQualifiedAttribute | QualifiedAttribute | AttributePath).examples("<attribute>") ^^^ Field.literal("todo")
    lazy val NonQualifiedAttribute = Identifier(DQuoteChar) map Q.attr
    lazy val QualifiedAttribute = (rep1sep(JavaIdentifier, '.') <~ '#') ~ JavaIdentifier map { case clsParts ~ attr => Q.attr(clsParts.mkString("."), attr) }
    lazy val AttributePath = rep1sep(Identifier(DQuoteChar), '.') map { parts => new AttributePathValueExp(parts.head, new java.util.ArrayList(parts.tail.asJava)) }

    lazy val Literal = BooleanLiteral | LongLiteral | DoubleLiteral | StringLiteral
    lazy val BooleanLiteral = BooleanValue map (Field.literal)
    lazy val LongLiteral = LongValue map (Field.literal)
    lazy val DoubleLiteral = DoubleValue map (Field.literal)
    lazy val StringLiteral = StringValue map (Field.literal)
  }


  def Projection(svr: MBeanServerConnection, query: Option[MBeanQuery]): Parser[Projection] = {
    new ProjectionProductions(query.map(_.results).getOrElse(svr.allResults)).Projection
  }

  private class ProjectionProductions(results: Results) {

    import scalaz.std.option._
    val O = Monad[Option]

    val attributeNames: Set[String] = sys.error("todo some function of results")

    lazy val Projection = {
      (token("*") ^^^ Beans.Projection.all) |
      (rep1sep(Attribute, ws.* ~ ',' ~ ws.*) map { attrMappings =>
        Beans.Projection.fromFields(attrMappings: _*)
      })
    }

    lazy val Attribute: Parser[Field[(String,Any)]] =
      token(UnnamedAttribute) ~ (token(" as ") ~> Identifier(SQuoteChar, DQuoteChar)).? map {
        case f ~ Some(t) => f map { case (_, v) => (t, v) }
        case f ~ None => f
      }

    lazy val UnnamedAttribute: Parser[Field[(String,Any)]] = new ExpressionParser {
      override type Expression = Field[(String,Any)]
      override def multiply(lhs: Expression, rhs: Expression) =
        lhs.pmap2(rhs) { (a,b) =>
          O.apply2(liftToBigDecimal(a._2), liftToBigDecimal(b._2))(_ * _).map((a._1 + " * " + b._1, _))
        }

      override def divide(lhs: Expression, rhs: Expression) =
        lhs.pmap2(rhs) { (a,b) =>
          O.apply2(liftToBigDecimal(a._2), liftToBigDecimal(b._2))(_ / _).map((a._1 + " / " + b._1, _))
        }
      override def add(lhs: Expression, rhs: Expression) =
        lhs.pmap2(rhs) { (a,b) =>
          O.apply2(liftToBigDecimal(a._2), liftToBigDecimal(b._2))(_ + _).map((a._1 + " + " + b._1, _))
        }
      override def subtract(lhs: Expression, rhs: Expression) =
        lhs.pmap2(rhs) { (a,b) =>
          O.apply2(liftToBigDecimal(a._2), liftToBigDecimal(b._2))(_ - _).map((a._1 + " - " + b._1, _))
        }
      private lazy val SimpleValue: Parser[Expression] =
        (IntValue | LongValue | DoubleValue) map { v => Field.literal(v.toString -> v) }
      override lazy val Value = SimpleAttribute | SimpleValue

    }.Expr.examples(Set("<value>", "<attribute>") ++ attributeNames)

    // todo - generalize this to work on subqueries
    lazy val SimpleAttribute: Parser[Field[(String,Any)]] =
      rep1sep(Identifier(SQuoteChar, DQuoteChar), '.') map { ids =>
        sys.error("todo")
      }
      //  attrs => (for {
      //    hv <- attrs.get(ids.head)
      //    v <- JMX.extractValue(hv, ids.tail)
      //  } yield (ids.mkString(".") -> v))
      //})
  }

  def Invocation(svr: MBeanServerConnection, query: Option[MBeanQuery]): Parser[(String, Seq[AnyRef])] =
    OperationName(svr, query) ~ (token("(") ~> repsep(ws.* ~> InvocationParameter(svr), ws.* ~ ',') <~ token(")"))

  private def OperationName(svr: MBeanServerConnection, query: Option[MBeanQuery]): Parser[String] = {
    val ops: Set[String] = safely(Set.empty[String]) {
      for {
        q <- query.toSet: Set[MBeanQuery]
        n <- svr.queryNames(q)
        i <- svr.mbeanInfo(n).toSet: Set[MBeanInfo]
        o <- i.getOperations
      } yield o.getName
    }
    Identifier(SQuoteChar, DQuoteChar).examples(ops + "<operation name>")
  }

  private def InvocationParameter(svr: MBeanServerConnection): Parser[AnyRef] = {
    (BooleanValue map { v => (java.lang.Boolean.valueOf(v): AnyRef) }) |
    (IntValue map { v => (java.lang.Integer.valueOf(v): AnyRef) }) |
    (LongValue map { v => (java.lang.Long.valueOf(v): AnyRef) }) |
    (DoubleValue map { v => (java.lang.Double.valueOf(v): AnyRef) }) |
    (StringValue) |
    ArrayP(BooleanValue) |
    ArrayP(IntValue) |
    ArrayP(LongValue) |
    ArrayP(DoubleValue) |
    ArrayP(StringValue)
  }.examples("<value>")

  private def ArrayP[A: Manifest](p: Parser[A]): Parser[Array[A]] =
    (token("{") ~> repsep(ws.* ~> p, ws.* ~> ',') <~ ws.* <~ token("}")) map { _.toArray }

  private def Identifier(quotes: Char*) = (JavaIdentifier | QuotedIdentifier(quotes: _*)).examples("identifier")

  private lazy val JavaIdentifier: Parser[String] = (
    charClass(c => c.isLetter || c == '_' | c == '$', "letter, underscore, or dollar sign") ~
    charClass(c => c.isLetter || c == '_' | c == '$' || c.isDigit, "letter, digit, underscore, or dollar sign").* map {
      case x ~ xs => (x +: xs).mkString
    }
  ).examples("identifier")

  private def QuotedIdentifier(quotes: Char*): Parser[String] = {
    def quotedWith(q: Char) = q ~> (charClass(_ != q) | (q ~ q ^^^ q)).* <~ q
    (quotes map quotedWith).reduceLeft { _ | _ }.string.examples("\"identifier\"")
  }

  private lazy val SQuoteChar = '\''

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

  private def Parenthesized[A](p: => Parser[A]) = for {
    _ <- token("(") <~ ws.*
    pp <- p <~ ws.* <~ token(")")
  } yield pp

  private[cli] trait ExpressionParser {
    type Expression
    def multiply(lhs: Expression, rhs: Expression): Expression
    def divide(lhs: Expression, rhs: Expression): Expression
    def add(lhs: Expression, rhs: Expression): Expression
    def subtract(lhs: Expression, rhs: Expression): Expression
    def Value: Parser[Expression]

    lazy val Expr: Parser[Expression] =
      (Term ~
        (ws.* ~> (
          ("+" ~> ws.* ~> Term map { rhs => add(_: Expression, rhs) }) |
          ("-" ~> ws.* ~> Term map { rhs => subtract(_: Expression, rhs) })
        ) <~ ws.*).*
      ) map { case first ~ sq => sq.foldLeft(first) { (acc, f) => f(acc) } }

    lazy val Term: Parser[Expression] =
      (Factor ~
        (ws.* ~> (
          ("*" ~> ws.* ~> Factor map { rhs => multiply(_: Expression, rhs) }) |
          ("/" ~> ws.* ~> Factor map { rhs => divide(_: Expression, rhs) })
        ) <~ ws.*).*
      ) map { case first ~ sq => sq.foldLeft(first) { (acc, f) => f(acc) } }

    lazy val Factor: Parser[Expression] = Value | Parenthesized(Expr).examples("(<value>)")
  }

  private def safely[A](onError: => A)(f: => A): A =
    try f catch { case e: Exception => onError }
}
