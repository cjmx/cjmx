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

package cjmx.util.jmx

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

import java.util.Hashtable
import javax.management.remote.*
import com.sun.tools.attach.*

object Attach:
  def localVMs: List[VirtualMachineDescriptor] =
    VirtualMachine.list.asScala.toList.sortBy(_.id)

  def localVMIDs: List[JMX.VMID] =
    localVMs.map(vmd => JMX.VMID(vmd.id))

  def remoteConnect(
      addr: String,
      credentials: Option[JMXCredentials]
  ): Either[String, JMXConnector] =
    val env = credentials.map { cred =>
      val ht = new Hashtable[String, Array[String]]
      val credentials = Array(cred.username, cred.password)
      ht.put(JMXConnector.CREDENTIALS, credentials)
      ht
    }
    try Right(JMXConnectorFactory.connect(new JMXServiceURL(addr), env.orNull))
    catch case NonFatal(t) => Left(Option(t.getMessage).getOrElse(""))

  def localConnect(vmid: String): Either[String, JMXConnector] = for
    vmd <- localVMs
      .find(_.id == vmid)
      .toRight("No virtual machine with VM ID %s".format(vmid))
    vm <-
      try Right(VirtualMachine.attach(vmd))
      catch { case NonFatal(t) => Left(Option(t.getMessage).getOrElse("")) }
    addr <- localConnectorAddress(vm)
    cnx <-
      try Right(JMXConnectorFactory.connect(new JMXServiceURL(addr)))
      catch { case NonFatal(t) => Left(Option(t.getMessage).getOrElse("")) }
  yield cnx

  def localConnectorAddress(vm: VirtualMachine): Either[String, String] =
    def getLocalConnectorAddress = Option(
      vm.getAgentProperties.getProperty("com.sun.management.jmxremote.localConnectorAddress")
    )
    val localConnectorAddress = getLocalConnectorAddress.orElse:
      vm.startLocalManagementAgent()
      getLocalConnectorAddress
    localConnectorAddress.toRight("Failed to connect to VM ID %s.".format(vm.id))
