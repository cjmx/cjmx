package cjmx.util

import scala.collection.JavaConverters._
import scalaz.Validation
import scalaz.syntax.std.option._
import scalaz.syntax.bifunctor._
import scalaz.syntax.validation._

import java.io.File
import javax.management.remote._
import com.sun.tools.attach._

object JMX {

  def localVMs: Seq[VirtualMachineDescriptor] =
    VirtualMachine.list.asScala.toSeq.sortBy { _.id }

  def localConnect(vmid: String): Validation[String, JMXConnector] = for {
    vmd <- localVMs.find { _.id == vmid }.toSuccess("No virtual machine with VM ID %s".format(vmid))
    vm <- Validation.fromTryCatch(VirtualMachine.attach(vmd)).<-: { _.getMessage }
    addr <- localConnectorAddress(vm)
    cnx <- Validation.fromTryCatch(JMXConnectorFactory.connect(new JMXServiceURL(addr))).<-: { _.getMessage }
  } yield cnx

  def localConnectorAddress(vm: VirtualMachine): Validation[String, String] = {
    def getLocalConnectorAddress = Option(vm.getAgentProperties.getProperty("com.sun.management.jmxremote.localConnectorAddress"))
    val localConnectorAddress = getLocalConnectorAddress orElse {
      val agent = vm.getSystemProperties.getProperty("java.home") + File.separator + "lib" + File.separator + "management-agent.jar"
      vm.loadAgent(agent)
      getLocalConnectorAddress
    }
    localConnectorAddress.toSuccess("Failed to connect to VM ID %s.".format(vm.id))
  }

  def humanizeType(t: String): String = {
    try Class.forName(t).getSimpleName
    catch {
      case cnfe: ClassNotFoundException => t
    }
  }
}
