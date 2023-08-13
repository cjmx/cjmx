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

import scala.collection.immutable.Seq

import javax.management.{ObjectName, Attribute, MBeanInfo}

import cjmx.util.jmx.JMX._

object TextMessageFormatter extends MessageFormatter {

  override def formatNames(names: Seq[ObjectName]) =
    names.map(_.toString)

  override def formatAttributes(attrsByName: Seq[(ObjectName, Seq[Attribute])]) = {
    val out = new OutputBuilder
    attrsByName.foreach { case (name, attrs) =>
      out <+ name.toString
      out <+ ("-" * name.toString.size)
      out.indented {
        attrs.foreach(attr => out <+ attr.toString)
      }
      out <+ ""
    }
    out.lines
  }

  override def formatInfo(info: Seq[(ObjectName, MBeanInfo)], detailed: Boolean) = {
    val out = new OutputBuilder
    for ((name, inf) <- info) {
      val nameLine = "Object name: %s".format(name)
      out <+ nameLine
      out <+ ("-" * nameLine.size)
      out <+ "Description: %s".format(inf.getDescription)
      out <+ ""

      val attributes = inf.getAttributes
      if (attributes.nonEmpty) {
        out <+ "Attributes:"
        out.indented {
          attributes.foreach { attr =>
            out <+ "%s: %s".format(attr.getName, JType(attr.getType).toString)
            if (detailed) out.indented {
              out <+ "Description: %s".format(attr.getDescription)
            }
          }
        }
        out <+ ""
      }

      val operations = inf.getOperations
      if (operations.nonEmpty) {
        out <+ "Operations:"
        out.indented {
          operations.foreach { op =>
            out <+ "%s(%s): %s".format(
              op.getName,
              op.getSignature
                .map(pi => "%s: %s".format(pi.getName, JType(pi.getType).toString))
                .mkString(", "),
              JType(op.getReturnType).toString
            )
            if (detailed) out.indented {
              out <+ "Description: %s".format(op.getDescription)
            }
          }
        }
        out <+ ""
      }

      val notifications = inf.getNotifications
      if (notifications.nonEmpty) {
        out <+ "Notifications:"
        out.indented {
          notifications.foreach { nt =>
            out <+ nt.getName
            if (detailed) out.indented {
              out <+ "Description: %S".format(nt.getDescription)
              out <+ "Notification types:"
              out.indented {
                nt.getNotifTypes.foreach(out <+ _)
              }
            }
          }
        }
        out <+ ""
      }
    }
    out.lines
  }

  override def formatInvocationResults(namesAndResults: Seq[(ObjectName, InvocationResult)]) =
    namesAndResults.map { case (name, result) => "%s: %s".format(name, result) }
}
