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
package actions

import scala.collection.JavaConverters._

import java.io.{ BufferedReader, InputStreamReader }

case class Help(topic: Option[String]) extends Action {
  def apply(context: ActionContext) = {
    val resourceName = "/cjmx/help/%s.md".format(topic.getOrElse("help"))
    val resource = Option(getClass.getResourceAsStream(resourceName))
    val vResource = resource.toRight("No help for %s".format(topic.getOrElse("")))
    vResource match {
      case Left(err) =>
        ActionResult(context.withStatusCode(1), List(err))
      case Right(resource) =>
        val lines: List[String] = {
          val reader = new BufferedReader(new InputStreamReader(resource))
          try {
            reader.lines.collect(java.util.stream.Collectors.toList[String]).asScala.toList
          } finally {
            reader.close()
          }
        }
        ActionResult(context.withStatusCode(0), lines)
    }
  }
}

