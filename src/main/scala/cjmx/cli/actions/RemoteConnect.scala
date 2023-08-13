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

package cjmx
package cli
package actions

import cjmx.util.jmx.{Attach, JMXCredentials, JMXConnection}

case class RemoteConnect(host: String, port: Int, username: Option[String], quiet: Boolean)
    extends Action:
  private val uri = s"service:jmx:rmi:///jndi/rmi://$host:$port/jmxrmi"

  def apply(context: ActionContext) =
    def buildCredentials(username: Option[String]): Either[String, Option[JMXCredentials]] =
      username match
        case Some(user) =>
          val password: Option[String] =
            context.readLine("Password: ", Some('*')).filter(_.nonEmpty)
          password match
            case Some(p) => Right(Some(JMXCredentials(user, p)))
            case None    => Left("No password specified.")
        case None => Right(None)

    val result = for
      credentials <- buildCredentials(username)
      connection <- Attach.remoteConnect(uri, credentials)
    yield connection

    result match
      case Left(err) => ActionResult(context.withStatusCode(1), List(err))
      case Right(cnx) =>
        val server = cnx.getMBeanServerConnection
        ActionResult(
          context.connected(JMXConnection.fromConnector(cnx)),
          if quiet then List.empty
          else
            List(
              s"Connected to remote virtual machine $uri",
              s"Connection id: ${cnx.getConnectionId}",
              s"Default domain: ${server.getDefaultDomain}",
              s"${server.getDomains.length} domains registered consisting of ${server.getMBeanCount} total MBeans"
            )
        )
