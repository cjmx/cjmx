/*
 * Copyright (c) 2012, cjmx
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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

