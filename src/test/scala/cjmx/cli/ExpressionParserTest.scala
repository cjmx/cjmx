package cjmx.cli

import scala.collection.JavaConverters._

import sbt.complete.Parser
import Parser._
import sbt.complete.DefaultParsers._

import org.scalatest._


class ExpressionParserTest extends FunSuite with ShouldMatchers {

  val validExamples = Seq(
    "1 + 2" -> "(+ 1 2)",
    "1+2" -> "(+ 1 2)",
    "1 + 2 / 3" -> "(+ 1 (/ 2 3))",
    "1+2/3" -> "(+ 1 (/ 2 3))",
    "1 + 2 - 3" -> "(- (+ 1 2) 3)",
    "1+2-3" -> "(- (+ 1 2) 3)",
    "1 / 2 * 2" -> "(* (/ 1 2) 2)",
    "1/2*2" -> "(* (/ 1 2) 2)",
    "1 / (2 * 2)" -> "(/ 1 (* 2 2))",
    "1/(2*2)" -> "(/ 1 (* 2 2))",
    "1 + 2 / 3 - 4 * 5" -> "(- (+ 1 (/ 2 3)) (* 4 5))"
  )

  validExamples foreach { case (ex, expectedOutput) =>
    test("valid - " + ex) {
      parse(ex) should be (Right(expectedOutput))
    }
  }

  private def parse(str: String): Either[String, String] =
    Parser.parse(str, new JMXParsers.ExpressionParser {
      type Expression = String
      def multiply(lhs: Expression, rhs: Expression) = "(* %s %s)".format(lhs, rhs)
      def divide(lhs: Expression, rhs: Expression) = "(/ %s %s)".format(lhs, rhs)
      def add(lhs: Expression, rhs: Expression) = "(+ %s %s)".format(lhs, rhs)
      def subtract(lhs: Expression, rhs: Expression) = "(- %s %s)".format(lhs, rhs)
      def Value: Parser[Expression] = Digit.+.string
    }.Expr)
}

