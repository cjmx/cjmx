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

package cjmx.util

import scala.collection.JavaConverters._

import java.rmi.UnmarshalException
import javax.management._

package object jmx {

  implicit class RichMBeanServerConnection(val self: MBeanServerConnection) extends AnyVal {

    def toScala = this

    def queryNames(name: Option[ObjectName], query: Option[QueryExp]): Set[ObjectName] =
      self.queryNames(name.orNull, query.orNull).asScala.toSet

    def queryNames(query: MBeanQuery): Set[ObjectName] =
      queryNames(query.from, query.where)

    def mbeanInfo(name: ObjectName): Option[MBeanInfo] =
      Option(self.getMBeanInfo(name))

    def attribute(name: ObjectName, attributeName: String): Option[Attribute] =
      try Some(new Attribute(attributeName, self.getAttribute(name, attributeName)))
      catch {
        case (_: UnmarshalException | _: JMException) =>
          None
      }

    def attributes(name: ObjectName, attributeNames: Array[String]): Seq[Attribute] =
      try self.getAttributes(name, attributeNames).asScala.toSeq.asInstanceOf[Seq[Attribute]]
      catch {
        case (_: UnmarshalException | _: JMException) =>
          attributeNames map { attrName =>
            attribute(name, attrName).getOrElse(new Attribute(attrName, "unavailable"))
          }
      }
  }
}
