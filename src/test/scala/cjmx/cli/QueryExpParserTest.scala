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
import javax.management._

import sbt.internal.util.complete.Parser

import org.scalatest._

class QueryExpParserTest extends FunSuite with Matchers {

  val validExamples = Seq(
    "Verbose = true" ->
      Query.eq(Query.attr("Verbose"), Query.value(true)),
    "Verbose = false" ->
      Query.eq(Query.attr("Verbose"), Query.value(false)),
    "LoadedClassCount = 0" ->
      Query.eq(Query.attr("LoadedClassCount"), Query.value(0)),
    "LoadedClassCount < 0" ->
      Query.lt(Query.attr("LoadedClassCount"), Query.value(0)),
    "LoadedClassCount > 0" ->
      Query.gt(Query.attr("LoadedClassCount"), Query.value(0)),
    "LoadedClassCount <= 0" ->
      Query.leq(Query.attr("LoadedClassCount"), Query.value(0)),
    "LoadedClassCount >= 0" ->
      Query.geq(Query.attr("LoadedClassCount"), Query.value(0)),
    "LoadedClassCount != 0" ->
      Query.not(Query.eq(Query.attr("LoadedClassCount"), Query.value(0))),
    "LoadedClassCount > 0 or UnloadedClassCount >= 0" ->
      Query.or(
        Query.gt(Query.attr("LoadedClassCount"), Query.value(0)),
        Query.geq(Query.attr("UnloadedClassCount"), Query.value(0))
      ),
    "LoadedClassCount - UnloadedClassCount > 20000" ->
      Query.gt(
        Query.minus(Query.attr("LoadedClassCount"), Query.attr("UnloadedClassCount")),
        Query.value(20000)
      ),
    "LoadedClassCount - 5000 > UnloadedClassCount" ->
      Query.gt(
        Query.minus(Query.attr("LoadedClassCount"), Query.value(5000)),
        Query.attr("UnloadedClassCount")
      ),
    "(UnloadedClassCount / (LoadedClassCount + UnloadedClassCount)) * 100 < 1" ->
      Query.lt(
        Query.times(
          Query.div(
            Query.attr("UnloadedClassCount"),
            Query.plus(
              Query.attr("LoadedClassCount"),
              Query.attr("UnloadedClassCount")
            )
          ),
          Query.value(100)
        ),
        Query.value(1)
      ),
    "UnloadedClassCount / LoadedClassCount + UnloadedClassCount * 100 < 1" ->
      Query.lt(
        Query.plus(
          Query.div(
            Query.attr("UnloadedClassCount"),
            Query.attr("LoadedClassCount")
          ),
          Query.times(
            Query.attr("UnloadedClassCount"),
            Query.value(100)
          )
        ),
        Query.value(1)
      ),
    "Used / Max * 100 < 50" ->
      Query.lt(
        Query.times(
          Query.div(
            Query.attr("Used"),
            Query.attr("Max")
          ),
          Query.value(100)
        ),
        Query.value(50)
      ),
    "Name = 'Mac OS X'" ->
      Query.eq(Query.attr("Name"), Query.value("Mac OS X")),
    "\"Name\" = 'Mac OS X'" ->
      Query.eq(Query.attr("Name"), Query.value("Mac OS X")),
    "LoadedClassCount between 10000 and 15000" ->
      Query.between(Query.attr("LoadedClassCount"), Query.value(10000), Query.value(15000)),
    "Foo between Bar and Baz" ->
      Query.between(Query.attr("Foo"), Query.attr("Bar"), Query.attr("Baz")),
    "Foo not between Bar and Baz" ->
      Query.not(Query.between(Query.attr("Foo"), Query.attr("Bar"), Query.attr("Baz"))),
    "Name like 'Windows*'" ->
      Query.`match`(Query.attr("Name"), Query.value("Windows*")),
    "Name in ('Windows', 'Mac OS X')" ->
      Query.in(Query.attr("Name"), Array(Query.value("Windows"), Query.value("Mac OS X"))),
    "instanceof 'java.lang.management.MemoryMXBean'" ->
      Query.isInstanceOf(Query.value("java.lang.management.MemoryMXBean")),
    "Name startsWith 'Mac'" ->
      Query.initialSubString(Query.attr("Name"), Query.value("Mac")),
    "not Name startsWith 'Mac'" ->
      Query.not(Query.initialSubString(Query.attr("Name"), Query.value("Mac"))),
    "Name endsWith 'X'" ->
      Query.finalSubString(Query.attr("Name"), Query.value("X")),
    "not Name endsWith 'X'" ->
      Query.not(Query.finalSubString(Query.attr("Name"), Query.value("X"))),
    "Name contains 'X'" ->
      Query.anySubString(Query.attr("Name"), Query.value("X")),
    "not Name contains 'X'" ->
      Query.not(Query.anySubString(Query.attr("Name"), Query.value("X"))),
    "sun.management.MemoryImpl#ObjectPendingFinalizationCount > 0" ->
      Query.gt(
        Query.attr("sun.management.MemoryImpl", "ObjectPendingFinalizationCount"),
        Query.value(0)
      ),
    "A > 0 and B > 0 and C > 0" ->
      Query.and(
        Query.and(
          Query.gt(
            Query.attr("A"),
            Query.value(0)
          ),
          Query.gt(
            Query.attr("B"),
            Query.value(0)
          )
        ),
        Query.gt(
          Query.attr("C"),
          Query.value(0)
        )
      ),
    "A > 0 and B > 0 or C > 0 and D > 0" ->
      Query.or(
        Query.and(
          Query.gt(
            Query.attr("A"),
            Query.value(0)
          ),
          Query.gt(
            Query.attr("B"),
            Query.value(0)
          )
        ),
        Query.and(
          Query.gt(
            Query.attr("C"),
            Query.value(0)
          ),
          Query.gt(
            Query.attr("D"),
            Query.value(0)
          )
        )
      ),
    "A > 0 or B > 0 and C > 0 or D > 0" ->
      Query.or(
        Query.or(
          Query.gt(
            Query.attr("A"),
            Query.value(0)
          ),
          Query.and(
            Query.gt(
              Query.attr("B"),
              Query.value(0)
            ),
            Query.gt(
              Query.attr("C"),
              Query.value(0)
            )
          )
        ),
        Query.gt(
          Query.attr("D"),
          Query.value(0)
        )
      )
  )

  validExamples.foreach { case (ex, query) =>
    test("valid - " + ex) {
      parse(ex).right.map(_.toString) should be(Right(query.toString))
    }
  }

  private def parse(str: String): Either[String, QueryExp] =
    Parser.parse(
      str,
      JMXParsers.QueryExpParser(
        ManagementFactory.getPlatformMBeanServer,
        new ObjectName("java.lang:*")
      )
    )
}
