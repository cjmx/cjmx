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

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import sbt.internal.util.complete.Parser
import cjmx.util.jmx.JMXConnection

class ExampleEmbedding extends AnyFunSuite with Matchers {

  test("Example of running mbean actions against current jvm") {
    val result =
      runMBeanAction("mbeans 'java.lang:type=Memory' select *").fold(e => Vector(e), identity)
    result(0) should be("java.lang:type=Memory")
  }

  def runMBeanAction(str: String): Either[String, Vector[String]] = {
    val cnx = JMXConnection.PlatformMBeanServerConnection
    for {
      action <- Parser.parse(str, Parsers.MBeanAction(cnx.mbeanServer))
      msgs <- {
        val initialCtx = ActionContext.embedded(connectionState = ConnectionState.Connected(cnx))
        val ActionResult(ctx, msgs) = action(initialCtx)
        if (ctx.lastStatusCode != 0)
          Left("Action failed with status code: " + ctx.lastStatusCode)
        else
          Right(msgs)
      }
    } yield msgs.toVector
  }
}
