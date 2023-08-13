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
import javax.management.{MalformedObjectNameException, ObjectName}

import sbt.internal.util.complete.Parser

import org.scalatest._

import JMXParsers._


class ObjectNameParserTest extends FunSuite with Matchers {

  val validExamples = Seq(
    // Basic examples
    "java.lang:*",
    "java.lang:type=Memory",
    "java.lang:type=Memory,*",
    "java.lang:type=*",
    "java.lang:type=MemoryPool,name=*,*",

    // Quoting
    """Domain:type="foo"""",
    """Domain:type="foo",*""",
    """Domain:type="f\"oo",*""",
    """Domain:type="foo,bar",*""",
    """Domain:type="foo:bar",*""",
    """Domain:type="foo=bar",*""",
    """Domain:type="foo?bar",*""",
    """Domain:type="foo\?bar",*""",
    """Domain:type="foo*bar",*""",
    """Domain:type="foo\*bar",*""",

    // Escaping
    "Domain:type=a\\*b",
    "Domain:type=a\\*b,*",
    "Domain:type=a\\?b,*",

    // From JMX specification
    "*:*",
    ":*",
    "??Domain:*",
    "*Domain*:*",
    "*:description=Printer,type=laser,*",
    "*Domain:description=Printer,*",
    "*Domain:description=P*,*"
  )

  validExamples foreach { ex =>
    test("valid - " + ex) {
      parse(ex) should be (Right(new ObjectName(ex)))
    }
  }

  val invalidExamples = Seq(
    "Domain:desc*=*,*", // wildcard not allowed in key
    """Domain:"desc""", // unclosed quoted key
    ":key=v1,key=v2" // duplicate key
  )

  invalidExamples foreach { ex =>
    test("invalid - " + ex) {
      // sanity check example is invalid
      a[MalformedObjectNameException] should be thrownBy { new ObjectName(ex) }
      parse(ex) should be ('left)
    }
  }

  def parse(str: String): Either[String, ObjectName] =
    Parser.parse(str, ObjectNameParser(ManagementFactory.getPlatformMBeanServer))

  val completionExamples = Seq(
    ("java.lang:", Set("*", "<key>=", "name=", "type=")),
    ("java.lang:type=", Set("*", "<value>", "ClassLoading", "Compilation", "GarbageCollector", "Memory", "MemoryManager", "MemoryPool", "OperatingSystem", "Runtime", "Threading")),
    ("java.lang:type=M", Set("Memory", "MemoryManager", "MemoryPool")),
    ("java.lang:type=MemoryPool,", Set("*", "<key>=", "name=")),
    ("java.lang:type=MemoryPool,name=Code Cache,", Set("*", "<key>="))
  )

  completionExamples foreach { case (str, expected) =>
    test("completion - " + str) {
      completions(str) should be (expected)
    }
  }

  def completions(str: String): Set[String] =
    Parser.completions(ObjectNameParser(ManagementFactory.getPlatformMBeanServer), str, Int.MaxValue).get.map { _.display }
}
