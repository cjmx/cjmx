package cjmx.util.jmx

import scala.collection.JavaConverters._
import scalaz.{ \/, @@ }
import scalaz.syntax.std.option._
import scalaz.syntax.bifunctor._

import java.io.File
import javax.management.remote._
import com.sun.tools.attach._

object Attach {

  def localVMs: List[VirtualMachineDescriptor] =
    VirtualMachine.list.asScala.toList.sortBy { _.id }

  def localVMIDs: List[String @@ JMXTags.VMID] =
    localVMs map { vmd => JMXTags.VMID(vmd.id) }

  def localConnect(vmid: String): String \/ JMXConnector = for {
    vmd <- localVMs.find { _.id == vmid }.toRightDisjunction("No virtual machine with VM ID %s".format(vmid))
    vm <- \/.fromTryCatch(VirtualMachine.attach(vmd)).<-: { _.getMessage }
    addr <- localConnectorAddress(vm)
    cnx <- \/.fromTryCatch(JMXConnectorFactory.connect(new JMXServiceURL(addr))).<-: { _.getMessage }
  } yield cnx

  def localConnectorAddress(vm: VirtualMachine): String \/ String = {
    def getLocalConnectorAddress = Option(vm.getAgentProperties.getProperty("com.sun.management.jmxremote.localConnectorAddress"))
    val localConnectorAddress = getLocalConnectorAddress orElse {
      val agent = vm.getSystemProperties.getProperty("java.home") + File.separator + "lib" + File.separator + "management-agent.jar"
      vm.loadAgent(agent)
      getLocalConnectorAddress
    }
    localConnectorAddress.toRightDisjunction("Failed to connect to VM ID %s.".format(vm.id))
  }
}

