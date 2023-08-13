package cjmx.util.jmx

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

import java.util.Hashtable
import javax.management.remote._
import com.sun.tools.attach._

object Attach {
  def localVMs: List[VirtualMachineDescriptor] =
    VirtualMachine.list.asScala.toList.sortBy { _.id }

  def localVMIDs: List[JMX.VMID] =
    localVMs map { vmd => JMX.VMID(vmd.id) }

  def remoteConnect(addr: String, credentials: Option[JMXCredentials]): Either[String, JMXConnector] =  {
    val env = credentials.map { cred =>
        val ht = new Hashtable[String, Array[String]]
        val credentials = Array(cred.username, cred.password)
        ht.put(JMXConnector.CREDENTIALS, credentials)
        ht
      }
    try Right(JMXConnectorFactory.connect(new JMXServiceURL(addr), env.orNull))
    catch {
      case NonFatal(t) => Left(Option(t.getMessage).getOrElse(""))
    }
  }

  def localConnect(vmid: String): Either[String, JMXConnector] = for {
    vmd <- localVMs.find { _.id == vmid }.toRight("No virtual machine with VM ID %s".format(vmid)).right
    vm <- (try Right(VirtualMachine.attach(vmd)) catch { case NonFatal(t) => Left(Option(t.getMessage).getOrElse("")) }).right
    addr <- localConnectorAddress(vm).right
    cnx <- (try Right(JMXConnectorFactory.connect(new JMXServiceURL(addr))) catch { case NonFatal(t) => Left(Option(t.getMessage).getOrElse("")) }).right
  } yield cnx

  def localConnectorAddress(vm: VirtualMachine): Either[String, String] = {
    def getLocalConnectorAddress = Option(vm.getAgentProperties.getProperty("com.sun.management.jmxremote.localConnectorAddress"))
    val localConnectorAddress = getLocalConnectorAddress orElse {
      vm.startLocalManagementAgent()
      getLocalConnectorAddress
    }
    localConnectorAddress.toRight("Failed to connect to VM ID %s.".format(vm.id))
  }
}

