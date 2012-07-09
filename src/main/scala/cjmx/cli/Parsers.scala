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

  val invoke =
    (svr: MBeanServerConnection) =>
      token("invoke ") ~> JMXParsers.Invocation(svr) ~ (token(" on ") ~> nameAndQuery(svr)) map { case ((opName, args)) ~ (name ~ query) => actions.InvokeOperation(Some(name), query, opName, args) }

  private lazy val mbeanAction =
    (svr: MBeanServerConnection) => for {
      _ <- token("mbeans ")
      nameAndQuery <- token("from ") ~> nameAndQuery(svr) <~ SpaceClass.*
      name = nameAndQuery._1
      query = nameAndQuery._2
      action <- postfixNames(svr, name, query) | postfixSelect(svr, name, query) | postfixInspect(svr, name, query) | postfixInvoke(svr, name, query)
    } yield action

  private lazy val postfixNames =
    (svr: MBeanServerConnection, name: ObjectName, query: Option[QueryExp]) =>
      token("names") ^^^ actions.ManagedObjectNames(Some(name), query)

  private lazy val postfixInspect =
    (svr: MBeanServerConnection, name: ObjectName, query: Option[QueryExp]) =>
      (token("inspect") ~> flag(" -d")) map {
        case detailed => actions.InspectMBeans(Some(name), query, detailed)
      }

  private lazy val postfixSelect =
    (svr: MBeanServerConnection, name: ObjectName, query: Option[QueryExp]) =>
      (token("select ") ~> SpaceClass.* ~> JMXParsers.Projection(svr, name, query)) map {
        case projection => actions.Query(Some(name), query, projection)
      }

  private lazy val postfixInvoke =
    (svr: MBeanServerConnection, name: ObjectName, query: Option[QueryExp]) =>
      (token("invoke ") ~> SpaceClass.* ~> JMXParsers.Invocation(svr)) map {
        case opName ~ args => actions.InvokeOperation(Some(name), query, opName, args)
      }

  val disconnect = token("disconnect") ^^^ actions.Disconnect

  val connected =
    (cnx: JMXConnector) => {
      val svr = cnx.getMBeanServerConnection
      names(svr) | inspect(svr) | query(svr) | invoke(svr) | mbeanAction(svr) | disconnect | globalActions !!! "Invalid input"
    }
}


