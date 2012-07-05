package cjmx.cli

import sbt._
import sbt.complete.Parser
import sbt.complete.DefaultParsers._

import scalaz._
import Scalaz._

import com.sun.tools.attach._
import scala.collection.JavaConverters._

import javax.management._
import javax.management.remote.JMXConnector

import ObjectNameParser._


object Parsers {
  import Parser._

  private val ws = charClass(_.isWhitespace)+

  private val list: Parser[Action] =
    (token("list") | token("jps")) ^^^ actions.ListVMs

  private val vmid: Seq[VirtualMachineDescriptor] => Parser[String] =
    (vms: Seq[VirtualMachineDescriptor]) => token(charClass(_.isDigit, "virtual machine id").+.string.examples(vms.map { _.id.toString }: _*))

  private val connect: Seq[VirtualMachineDescriptor] => Parser[actions.Connect] =
    (vms: Seq[VirtualMachineDescriptor]) => (token("connect" ~> ' ') ~> (token(flag("-q ")) ~ vmid(vms))) map { case quiet ~ vmid => actions.Connect(vmid, quiet) }

  private val quit: Parser[Action] = (token("exit", _ => true) | token("done", _ => true) | token("quit")) ^^^ actions.Quit

  private val globalActions = quit

  val disconnected =
    (vms: Seq[VirtualMachineDescriptor]) => list | connect(vms) | globalActions !!! "Invalid input"

  val query =
    (svr: MBeanServerConnection) => (token("query" ~ ' ') ~> queryString(svr)) map actions.Query

  val queryString =
    (svr: MBeanServerConnection) => JmxObjectName(svr)

  val names =
    (svr: MBeanServerConnection) =>
      (token("names") ^^^ actions.ManagedObjectNames(None, None)) |
      (token("names ") ~> JmxObjectName(svr) map { name => actions.ManagedObjectNames(Some(name), None) })

  val inspect =
    (svr: MBeanServerConnection) =>
      token("inspect ") ~> (flag("-d ") ~ JmxObjectName(svr)) map { case detailed ~ name => actions.InspectMBeans(Some(name), None, detailed) }

  val disconnect = token("disconnect") ^^^ actions.Disconnect

  val connected =
    (cnx: JMXConnector) => {
      val svr = cnx.getMBeanServerConnection
      names(svr) | inspect(svr) | query(svr) | disconnect | globalActions !!! "Invalid input"
    }
}


