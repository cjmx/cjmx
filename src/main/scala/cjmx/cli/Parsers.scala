package cjmx.cli

import sbt._
import sbt.complete.Parser
import sbt.complete.DefaultParsers._

import scalaz.{Digit => _, _}
import Scalaz._

import com.sun.tools.attach._
import scala.collection.JavaConverters._

import javax.management._
import javax.management.remote.JMXConnector

import cjmx.util.jmx.MBeanQuery
import JMXParsers._


object Parsers {
  import Parser._

  private lazy val GlobalActions: Parser[Action] = Exit | Help

  private lazy val Exit: Parser[Action] =
    (token("exit") | token("done", _ => true)) ^^^ actions.Exit

  private lazy val Help: Parser[Action] =
    (token("help") ~> (' ' ~> any.+.string).?) map { topic => actions.Help(topic) }

  def Disconnected(vms: Seq[VirtualMachineDescriptor]): Parser[Action] =
    ListVMs | Connect(vms) | GlobalActions !!! "Invalid input"

  private val ListVMs: Parser[Action] =
    (token("list") | token("jps")) ^^^ actions.ListVMs

  private def VMID(vms: Seq[VirtualMachineDescriptor]): Parser[String] =
    token(Digit.+.string.examples(vms.map { _.id.toString }: _*))

  private def Connect(vms: Seq[VirtualMachineDescriptor]): Parser[actions.Connect] =
    (token("connect" ~> ' ') ~> (token(flag("-q ")) ~ VMID(vms))) map {
      case quiet ~ vmid => actions.Connect(vmid, quiet)
    }


  def Connected(cnx: JMXConnector): Parser[Action] = {
    val svr = cnx.getMBeanServerConnection
    MBeanAction(svr) | PrefixNames(svr) | PrefixInspect(svr) | PrefixSelect(svr) | PrefixInvoke(svr) | Disconnect | GlobalActions !!! "Invalid input"
  }

  private def MBeanAction(svr: MBeanServerConnection): Parser[Action] =
    for {
      _ <- token("mbeans ")
      query <- token("from ") ~> MBeanQueryP(svr) <~ SpaceClass.*
      action <- PostfixNames(svr, query) | PostfixSelect(svr, query) | PostfixInspect(svr, query) | PostfixInvoke(svr, query)
    } yield action


  private def MBeanQueryP(svr: MBeanServerConnection): Parser[MBeanQuery] =
    for {
      name <- QuotedObjectNameParser(svr)
      query <- (token(" where ") ~> JMXParsers.QueryExpParser(svr, name)).?
    } yield MBeanQuery(Some(name), query)

  private def PrefixNames(svr: MBeanServerConnection): Parser[actions.ManagedObjectNames] =
    (token("names") ^^^ actions.ManagedObjectNames(MBeanQuery.All)) |
    (token("names ") ~> MBeanQueryP(svr) map { case query => actions.ManagedObjectNames(query) })

  private def PostfixNames(svr: MBeanServerConnection, query: MBeanQuery): Parser[actions.ManagedObjectNames] =
    token("names") ^^^ actions.ManagedObjectNames(query)

  private def PrefixInspect(svr: MBeanServerConnection): Parser[actions.InspectMBeans] =
    token("inspect ") ~> (flag("-d ") ~ MBeanQueryP(svr)) map {
      case detailed ~ query => actions.InspectMBeans(query, detailed)
    }

  private def PostfixInspect(svr: MBeanServerConnection, query: MBeanQuery): Parser[actions.InspectMBeans] =
    (token("inspect") ~> flag(" -d")) map {
      case detailed => actions.InspectMBeans(query, detailed)
    }

  private def PrefixSelect(svr: MBeanServerConnection): Parser[actions.Query] =
    (SelectClause(svr, None) ~ (token(" from ") ~> MBeanQueryP(svr))) map {
      case projection ~ query => actions.Query(query, projection)
    }

  private def PostfixSelect(svr: MBeanServerConnection, query: MBeanQuery): Parser[actions.Query] =
    SelectClause(svr, Some(query)) map {
      case projection => actions.Query(query, projection)
    }

  private def PrefixInvoke(svr: MBeanServerConnection): Parser[actions.InvokeOperation] =
    token("invoke ") ~> JMXParsers.Invocation(svr) ~ (token(" on ") ~> MBeanQueryP(svr)) map {
      case ((opName, args)) ~ query => actions.InvokeOperation(query, opName, args)
    }

  private def PostfixInvoke(svr: MBeanServerConnection, query: MBeanQuery): Parser[actions.InvokeOperation] =
    (token("invoke ") ~> SpaceClass.* ~> JMXParsers.Invocation(svr)) map {
      case opName ~ args => actions.InvokeOperation(query, opName, args)
    }

  private def SelectClause(svr: MBeanServerConnection, query: Option[MBeanQuery]): Parser[Seq[Attribute] => Seq[Attribute]] =
    (token("select ") ~> SpaceClass.* ~> JMXParsers.Projection(svr, query))

  private lazy val Disconnect: Parser[Action] =
    token("disconnect") ^^^ actions.Disconnect
}

