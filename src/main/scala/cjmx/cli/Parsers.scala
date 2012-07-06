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

import JMXParsers._


object Parsers {
  import Parser._

  private val list: Parser[Action] =
    (token("list") | token("jps")) ^^^ actions.ListVMs

  private val vmid: Seq[VirtualMachineDescriptor] => Parser[String] =
    (vms: Seq[VirtualMachineDescriptor]) => token(charClass(_.isDigit, "virtual machine id").+.string.examples(vms.map { _.id.toString }: _*))

  private val connect: Seq[VirtualMachineDescriptor] => Parser[actions.Connect] =
    (vms: Seq[VirtualMachineDescriptor]) => (token("connect" ~> ' ') ~> (token(flag("-q ")) ~ vmid(vms))) map { case quiet ~ vmid => actions.Connect(vmid, quiet) }

  private val exit: Parser[Action] = (token("exit") | token("done", _ => true)) ^^^ actions.Exit

  private val globalActions = exit

  val disconnected =
    (vms: Seq[VirtualMachineDescriptor]) => list | connect(vms) | globalActions !!! "Invalid input"

  val query =
    (svr: MBeanServerConnection) => token("query ") ~> token("from ") ~> nameAndQuery(svr) map { case name ~ where => actions.Query(Some(name), where) }

  private val nameAndQuery: MBeanServerConnection => Parser[(ObjectName, Option[QueryExp])] =
    (svr: MBeanServerConnection) => for {
      name <- QuotedObjectNameParser(svr)
      query <- (token(" where ") ~> JMXParsers.QueryExpParser(svr, name)).?
    } yield (name, query)

  val names =
    (svr: MBeanServerConnection) =>
      (token("names") ^^^ actions.ManagedObjectNames(None, None)) |
      (token("names ") ~> nameAndQuery(svr) map { case name ~ where => actions.ManagedObjectNames(Some(name), where) })

  val inspect =
    (svr: MBeanServerConnection) =>
      token("inspect ") ~> (flag("-d ") ~ nameAndQuery(svr)) map { case detailed ~ (name ~ query) => actions.InspectMBeans(Some(name), query, detailed) }

  val disconnect = token("disconnect") ^^^ actions.Disconnect

  val connected =
    (cnx: JMXConnector) => {
      val svr = cnx.getMBeanServerConnection
      names(svr) | inspect(svr) | query(svr) | disconnect | globalActions !!! "Invalid input"
    }
}


