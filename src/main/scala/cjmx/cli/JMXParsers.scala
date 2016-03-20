package cjmx
package cli

import scala.collection.immutable.Seq
import scala.collection.JavaConverters._
import scala.collection.JavaConversions.mapAsScalaMap

import sbt.complete.Parser
import sbt.complete.DefaultParsers._

import javax.management.{ JMX => _, _ }

import MoreParsers._

import cjmx.ext.AttributePathValueExp
import cjmx.util.Math.liftToBigDecimal
import cjmx.util.jmx._


object JMXParsers {
  import Parser._

  def QuotedObjectNameParser(svr: MBeanServerConnection): Parser[ObjectName] =
    token('\'') ~> ObjectNameParser(svr) <~ token('\'')

  def ObjectNameParser(svr: MBeanServerConnection): Parser[ObjectName] =
    for {
      domain <- token(ObjectNameDomainParser(svr).? <~ ':') map { _ getOrElse "" }
      builder <- ObjectNameProductions.Properties(svr, ObjectNameBuilder(domain))
      oname <- builder.oname.fold(e => Parser.failure("invalid object name: " + e), n => Parser.success(n))
    } yield oname

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
          (key, value) <- mapAsScalaMap(name.getKeyPropertyList)
          if !soFar.properties.contains(key)
        } yield key
        keys.toSet + "<key>"
      }

    def PropertyValue(svr: MBeanServerConnection, soFar: ObjectNameBuilder, key: String): Parser[String] =
      PropertyPart(valuePart = true).examples {
        val values = for {
          nameSoFar <- soFar.addProperty(key, "*").addPropertyWildcardChar.oname.toOption.toSet: Set[ObjectName]
          name <- safely(Set.empty[ObjectName]) { svr.toScala.queryNames(Some(nameSoFar), None) }
          value <- mapAsScalaMap(name.getKeyPropertyList).get(key)
        } yield value
        values.toSet + "<value>"
      }

    def PropertyPart(valuePart: Boolean): Parser[String] =
      PropertyPartNonQuoted(valuePart) | PropertyPartQuoted(valuePart)

    def PropertyPartNonQuoted(valuePart: Boolean): Parser[String] =
      repFlatMap(None: Option[Char]) { lastChar =>
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
      (DQuoteChar ~> (repFlatMap(None: Option[Char]) { lastChar =>
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

    def oname: Either[MalformedObjectNameException, ObjectName] = {
      try Right(new ObjectName(toString))
      catch {
        case e: MalformedObjectNameException => Left(e)
      }
    }
  }


  def QueryExpParser(svr: MBeanServerConnection, name: ObjectName): Parser[QueryExp] = {
    val attributeNames = safely(Set.empty[String]) {
      svr.toScala.queryNames(Some(name), None).flatMap { n =>
        svr.getMBeanInfo(n).getAttributes.map { _.getName }.toSet
      }
    }
    new QueryExpProductions(attributeNames).Query
  }

  private class QueryExpProductions(attributeNames: Set[String]) {
    import javax.management.{Query => Q}

    lazy val Query: Parser[QueryExp] =
      (AndQuery ~ (
        (ws.* ~> token("or ") ~> ws.* ~> AndQuery <~ ws.*) map { q => Q.or(_: QueryExp, q) }
      ).*) map { case p ~ seq => seq.foldLeft(p) { (acc, q) => q(acc) } }

    lazy val AndQuery: Parser[QueryExp] =
      (Predicate ~ (
        (ws.* ~> token("and ") ~> ws.* ~> Predicate <~ ws.*) map { q => Q.and(_: QueryExp, q) }
      ).*) map { case p ~ seq => seq.foldLeft(p) { (acc, q) => q(acc) } }

    lazy val Not = token("not ") <~ ws.* ^^^ true

    lazy val Predicate: Parser[QueryExp] = {
      Parenthesized(Query).examples("(<predicate>)") |
      NotPredicate |
      InstanceOf |
      ValuePredicate
    }

   lazy val NotPredicate =
      (Not flatMap { _ => Predicate }).map(Q.not).examples("not <predicate>")

    lazy val InstanceOf =
      (token("instanceof ") ~> (token(ws.* ~> StringLiteral)).examples("'<type name>'")).map(Q.isInstanceOf)

    lazy val ValuePredicate =
      (token(Value) flatMap { lhs => ws.* ~> {
        val dependentOnOnlyValueExp =
          RelationalOperation(lhs) | (ws.+ ~> (Negatable(Between(lhs)) | Negatable(In(lhs))))
        lhs match {
          case av: AttributeValueExp =>
            dependentOnOnlyValueExp | (ws.+ ~> (Negatable(Match(av)) | Negatable(SubString(av))))
          case _ =>
            dependentOnOnlyValueExp
        }
      }})

    def RelationalOperation(lhs: ValueExp) = {
      def binaryOp(op: String, f: (ValueExp, ValueExp) => QueryExp): Parser[QueryExp] =
        token(op) ~> ws.* ~> Value map { rhs => f(lhs, rhs) }
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
      _ <- token("between ") <~ ws.*
      low <- Value <~ ws.* <~ token("and ") <~ ws.*
      hi <- Value
    } yield Q.between(lhs, low, hi)

    def In(lhs: ValueExp) = for {
      _ <- token("in ") <~ ws.* <~ token("(")
      values <- repsep(Value, ws.* ~ ',' ~ ws.*)
      _ <- ws.* ~ token(")")
    } yield Q.in(lhs, values.toArray)

    def Match(lhs: AttributeValueExp) = for {
      _ <- token("like ") <~ ws.*
      pat <- StringLiteral
    } yield Q.`match`(lhs, pat)

    def SubString(lhs: AttributeValueExp) =
      InitialSubString(lhs) | FinalSubString(lhs) | AnySubString(lhs)

    def InitialSubString(lhs: AttributeValueExp) = for {
      _ <- token("startsWith ") <~ ws.*
      ss <- StringLiteral
    } yield Q.initialSubString(lhs, ss)

    def FinalSubString(lhs: AttributeValueExp) = for {
      _ <- token("endsWith ") <~ ws.*
      ss <- StringLiteral
    } yield Q.finalSubString(lhs, ss)

    def AnySubString(lhs: AttributeValueExp) = for {
      _ <- token("contains ") <~ ws.*
      ss <- StringLiteral
    } yield Q.anySubString(lhs, ss)

    lazy val Value: Parser[ValueExp] = new ExpressionParser {
      override type Expression = ValueExp
      override def multiply(lhs: ValueExp, rhs: ValueExp) = Q.times(lhs, rhs)
      override def divide(lhs: ValueExp, rhs: ValueExp) = Q.div(lhs, rhs)
      override def add(lhs: ValueExp, rhs: ValueExp) = Q.plus(lhs, rhs)
      override def subtract(lhs: ValueExp, rhs: ValueExp) = Q.minus(lhs, rhs)
      override lazy val Value = Attribute | Literal
    }.Expr.examples(Set("<value>", "<attribute>") ++ attributeNames)

    lazy val Attribute = (NonQualifiedAttribute | QualifiedAttribute | AttributePath).examples("<attribute>")
    lazy val NonQualifiedAttribute = Identifier(DQuoteChar) map Q.attr
    lazy val QualifiedAttribute = (rep1sep(JavaIdentifier, '.') <~ '#') ~ JavaIdentifier map { case clsParts ~ attr => Q.attr(clsParts.mkString("."), attr) }
    lazy val AttributePath = rep1sep(Identifier(DQuoteChar), '.') map { parts => new AttributePathValueExp(parts.head, new java.util.ArrayList(parts.tail.asJava)) }

    lazy val Literal = BooleanLiteral | LongLiteral | DoubleLiteral | StringLiteral
    lazy val BooleanLiteral = BooleanValue map Q.value
    lazy val LongLiteral = LongValue map Q.value
    lazy val DoubleLiteral = DoubleValue map Q.value
    lazy val StringLiteral = StringValue map Q.value
  }


  def Projection(svr: MBeanServerConnection, query: Option[MBeanQuery]): Parser[Seq[Attribute] => Seq[Attribute]] = {
    val getAttributeNames = svr.getMBeanInfo(_: ObjectName).getAttributes.map { _.getName }.toSet
    val attributeNames = query.fold(Set.empty[String])(q => safely(Set.empty[String]) { svr.toScala.queryNames(q).flatMap(getAttributeNames) })
    new ProjectionProductions(attributeNames).Projection
  }

  private class ProjectionProductions(attributeNames: Set[String]) {

    lazy val Projection = {
      (token("*") ^^^ (identity[Seq[Attribute]] _)) |
      (rep1sep(Attribute, ws.* ~ ',' ~ ws.*) map { attrMappings =>
        (attrs: Seq[Attribute]) => {
          val attrsAsMap = attrs.map { attr => attr.getName -> attr.getValue }.toMap
          attrMappings.foldLeft(Seq.empty[Attribute]) { (acc, mapping) =>
            val attr = mapping(attrsAsMap).map { case (k, v) => new Attribute(k, v) }
            attr.fold(acc)(a => acc :+ a)
          }
        }
      })
    }

    lazy val Attribute: Parser[Map[String, AnyRef] => Option[(String, AnyRef)]] =
      token(UnnamedAttribute) ~ (token(" as ") ~> Identifier(SQuoteChar, DQuoteChar)).? map {
        case f ~ Some(t) => attrs => f(attrs) map { case (_, v) => (t, v) }
        case f ~ None => f
      }

    lazy val UnnamedAttribute: Parser[Map[String, AnyRef] => Option[(String, AnyRef)]] = new ExpressionParser {
      override type Expression = Map[String, AnyRef] => Option[(String, AnyRef)]
      override def multiply(lhs: Expression, rhs: Expression) = attrs => apply(Ops.Multiplication, attrs, lhs, rhs)
      override def divide(lhs: Expression, rhs: Expression) = attrs => apply(Ops.Division, attrs, lhs, rhs)
      override def add(lhs: Expression, rhs: Expression) = attrs => apply(Ops.Addition, attrs, lhs, rhs)
      override def subtract(lhs: Expression, rhs: Expression) = attrs => apply(Ops.Subtraction, attrs, lhs, rhs)
      private lazy val SimpleValue: Parser[Expression] =
        (IntValue | LongValue | DoubleValue) map { v => (attrs: Map[String, AnyRef]) => Option((v.toString, v.asInstanceOf[AnyRef])) }
      override lazy val Value = SimpleAttribute | SimpleValue

      private sealed trait Op {
        def name: String
        def apply(x: BigDecimal, y: BigDecimal): BigDecimal
      }

      private object Ops {
        object Multiplication extends Op {
          def name = "*"
          def apply(x: BigDecimal, y: BigDecimal) = x * y
        }

        object Division extends Op {
          def name = "/"
          def apply(x: BigDecimal, y: BigDecimal) = x / y
        }

        object Addition extends Op {
          def name = "+"
          def apply(x: BigDecimal, y: BigDecimal) = x + y
        }

        object Subtraction extends Op {
          def name = "-"
          def apply(x: BigDecimal, y: BigDecimal) = x - y
        }
      }

      private def apply(op: Op, attrs: Map[String, AnyRef], lhs: Expression, rhs: Expression) = {
        for {
          (leftName, leftValue) <- lhs(attrs)
          (rightName, rightValue) <- rhs(attrs)
          result <- for {
            l <- liftToBigDecimal(leftValue)
            r <- liftToBigDecimal(rightValue)
          } yield op(l, r)
        } yield ("%s %s %s".format(leftName, op.name, rightName), result)
      }
    }.Expr.examples(Set("<value>", "<attribute>") ++ attributeNames)

    lazy val SimpleAttribute: Parser[Map[String, AnyRef] => Option[(String, AnyRef)]] =
      (rep1sep(Identifier(SQuoteChar, DQuoteChar), '.') map { ids =>
        attrs => (for {
          hv <- attrs.get(ids.head)
          v <- JMX.extractValue(hv, ids.tail)
        } yield (ids.mkString(".") -> v))
      })
  }

  def Invocation(svr: MBeanServerConnection, query: Option[MBeanQuery]): Parser[(String, Seq[AnyRef])] =
    (OperationName(svr, query) ~ (token("(") ~> repsep(ws.* ~> InvocationParameter(svr), ws.* ~ ',') <~ token(")"))).map { case (k, v) => (k, v.toList) }

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
