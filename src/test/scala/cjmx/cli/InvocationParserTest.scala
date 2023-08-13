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

import java.lang.management.ManagementFactory

import sbt.internal.util.complete.Parser

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class InvocationParserTest extends AnyFunSuite with Matchers {

  val validExamples: Seq[(String, (String, Seq[Any]))] = Seq(
    "gc()" -> ("gc" -> Seq.empty[AnyRef]),
    "getThreadInfo(51235L)" -> ("getThreadInfo" -> Seq(51235L)),
    "getThreadInfo(51235)" -> ("getThreadInfo" -> Seq(51235)),
    "getThreadInfo({51235L, 11011L})" -> ("getThreadInfo" -> Seq(Seq(51235L, 11011L))),
    "getLoggerLevel('cjmx')" -> ("getLoggerLevel" -> Seq("cjmx")),
    "setLoggerLevel('cjmx', 'DEBUG')" -> ("setLoggerLevel" -> Seq("cjmx", "DEBUG"))
  )

  validExamples.foreach { case (ex, (expectedName, expectedArgs)) =>
    test("valid - " + ex) {
      val result = parse(ex)
      result.right.map(_._1) should be(Right(expectedName))
      result.right.map {
        _._2.collect {
          case a: Array[_] => a.toSeq
          case o           => o
        }
      } should be(Right(expectedArgs))
    }
  }

  private def parse(str: String): Either[String, (String, Seq[AnyRef])] =
    Parser.parse(str, JMXParsers.Invocation(ManagementFactory.getPlatformMBeanServer, None))
}
