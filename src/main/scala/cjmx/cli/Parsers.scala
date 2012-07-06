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
    (svr: MBeanServerConnection) => token("query" ~ ' ') ~> queryString(svr)

  val queryString =
    (svr: MBeanServerConnection) => for {
      name <- token("from '") ~> ObjectNameParser(svr) <~ token('\'')
      query <- (token(" where ") ~> JMXParsers.QueryExpParser(svr, name)).?
    } yield actions.Query(Some(name), query)

  val names =
    (svr: MBeanServerConnection) =>
      (token("names") ^^^ actions.ManagedObjectNames(None, None)) |
      (token("names ") ~> ObjectNameParser(svr) map { name => actions.ManagedObjectNames(Some(name), None) })

  val inspect =
    (svr: MBeanServerConnection) =>
      token("inspect ") ~> (flag("-d ") ~ ObjectNameParser(svr)) map { case detailed ~ name => actions.InspectMBeans(Some(name), None, detailed) }

  val disconnect = token("disconnect") ^^^ actions.Disconnect

  val connected =
    (cnx: JMXConnector) => {
      val svr = cnx.getMBeanServerConnection
      names(svr) | inspect(svr) | query(svr) | disconnect | globalActions !!! "Invalid input"
    }
}


