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

import scala.jdk.CollectionConverters._

import java.lang.management.ManagementFactory
import javax.management._
import javax.management.openmbean.{
  CompositeData,
  CompositeDataSupport,
  CompositeType,
  OpenType,
  SimpleType
}

import sbt.internal.util.complete.Parser

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable.Seq

import cjmx.util.jmx._
import cjmx.util.jmx.JMX.JAttribute

class ProjectionParserTest extends AnyFunSuite with Matchers {

  // Assumes HeapMemoryUsage.{init = 0, committed=1000000000, used=500000000, max=2000000000}
  val validExamplesBasedOnMemoryMXBean = Seq(
    "HeapMemoryUsage" -> Seq(
      "HeapMemoryUsage:",
      "committed: 1000000000",
      "init: 0",
      "max: 2000000000",
      "used: 500000000"
    ),
    "HeapMemoryUsage.init" -> Seq("HeapMemoryUsage.init: 0"),
    "HeapMemoryUsage.used, HeapMemoryUsage.max" -> Seq(
      "HeapMemoryUsage.used: 500000000",
      "HeapMemoryUsage.max: 2000000000"
    ),
    "HeapMemoryUsage.used as used, HeapMemoryUsage.max as max" -> Seq(
      "used: 500000000",
      "max: 2000000000"
    ),
    "HeapMemoryUsage.used / 1000000000" -> Seq("HeapMemoryUsage.used / 1000000000: 0.5"),
    "HeapMemoryUsage.used / HeapMemoryUsage.max" -> Seq(
      "HeapMemoryUsage.used / HeapMemoryUsage.max: 0.25"
    ),
    "HeapMemoryUsage.used / HeapMemoryUsage.max as percentUsed" -> Seq("percentUsed: 0.25"),
    """HeapMemoryUsage.used / 1000000 as "Used Heap (mb)", HeapMemoryUsage.max / 1000000 as "Max Heap (mb)", HeapMemoryUsage.used / HeapMemoryUsage.max * 100 as "Used Percentage"""" ->
      Seq("Used Heap (mb): 500", "Max Heap (mb): 2000", "Used Percentage: 25.00")
  )

  validExamplesBasedOnMemoryMXBean.foreach { case (ex, expectedOutput) =>
    test("valid - " + ex) {
      val attrs =
        Seq(heapMemoryUsage(init = 0, committed = 1000000000, used = 500000000, max = 2000000000))
      val result = parse(ex)
      val projected = result.toOption.get(attrs)
      val projectedAsStrings = projected.flatMap { attr =>
        JAttribute(attr).toString.split("%n".format()).map(_.trim)
      }
      projectedAsStrings should be(expectedOutput)
    }
  }

  private def parse(str: String): Either[String, Seq[Attribute] => Seq[Attribute]] =
    Parser.parse(
      str,
      JMXParsers.Projection(
        ManagementFactory.getPlatformMBeanServer,
        Some(MBeanQuery(new ObjectName("java.lang:type=Memory")))
      )
    )

  private def heapMemoryUsage(init: Long, committed: Long, used: Long, max: Long): Attribute =
    new Attribute("HeapMemoryUsage", memoryComposite(init, committed, used, max))

  private def memoryComposite(init: Long, committed: Long, used: Long, max: Long): CompositeData =
    new CompositeDataSupport(
      new CompositeType(
        "typeName",
        "desc",
        Array("init", "committed", "used", "max"),
        Array("init", "committed", "used", "max"),
        Array[OpenType[?]](SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG)
      ),
      Map(
        "init" -> 0L,
        "committed" -> 1000000000L,
        "used" -> 500000000L,
        "max" -> 2000000000L
      ).asJava
    )
}
