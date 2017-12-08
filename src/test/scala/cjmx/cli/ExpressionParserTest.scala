package cjmx.cli

import sbt.internal.util.complete.Parser
import sbt.internal.util.complete.Parser.richParser
import sbt.internal.util.complete.Parsers.Digit

import org.scalatest._


class ExpressionParserTest extends FunSuite with Matchers {

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

