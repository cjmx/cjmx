package cjmx.cli

import sbt._
import sbt.complete.Parser
import sbt.complete.DefaultParsers._

import scalaz.{Digit => _, _}
import Scalaz._

import scala.collection.JavaConverters._

import javax.management._

import cjmx.util.jmx._
import cjmx.util.jmx.Beans.{unnamed, Field, Projection}
import JMXParsers._


object Parsers {
  import Parser._

  private lazy val GlobalActions: Parser[Action] = Exit | Help | SetFormat | Status

  private lazy val Exit: Parser[Action] =
    (token("exit") | token("done", _ => true)) ^^^ actions.Exit

  private lazy val Help: Parser[Action] =
    (token("help") ~> (' ' ~> any.+.string).?) map { topic => actions.Help(topic) }

  private lazy val SetFormat: Parser[Action] =
    token("format ") ~> (("text" ^^^ TextMessageFormatter) | "json" ^^^ JsonMessageFormatter.standard | "cjson" ^^^ JsonMessageFormatter.compact) map actions.SetFormat

  private lazy val Status: Parser[Action] =
    token("status") ^^^ actions.LastStatus

  def Disconnected(vms: Seq[String @@ JMXTags.VMID]): Parser[Action] =
    ListVMs | Connect(vms) | GlobalActions !!! "Invalid input"

  private val ListVMs: Parser[Action] =
    (token("list") | token("jps")) ^^^ actions.ListVMs

  private def VMID(vms: Seq[String @@ JMXTags.VMID]): Parser[String] =
    token(Digit.+.string.examples(vms: _*))

  private def Connect(vms: Seq[String @@ JMXTags.VMID]): Parser[actions.Connect] =
    (token("connect" ~> ' ') ~> (token(flag("-q ")) ~ VMID(vms))) map {
      case quiet ~ vmid => actions.Connect(vmid, quiet)
    }


  def Connected(svr: MBeanServerConnection): Parser[Action] = {
    MBeanAction(svr) | PrefixNames(svr) | PrefixDescribe(svr) | PrefixSelect(svr) | PrefixSample(svr) | PrefixInvoke(svr) | Disconnect | GlobalActions !!! "Invalid input"
  }

  def MBeanAction(svr: MBeanServerConnection): Parser[Action] =
    for {
      _ <- token("mbeans ")
      query <- MBeanQueryP(svr) <~ SpaceClass.*
      action <- PostfixNames(svr, query) | PostfixSelect(svr, query) | PostfixSample(svr, query) | PostfixDescribe(svr, query) | PostfixInvoke(svr, query)
    } yield action


  private def MBeanQueryP(svr: MBeanServerConnection): Parser[MBeanQuery] =
    for {
      name <- QuotedObjectNameParser(svr)
      val results = svr.results(name) // need to load results in here, and save them
      query <- (token(" where ") ~> JMXParsers.QueryExpParser(results)).?
    } yield MBeanQuery(results, query.getOrElse(Field.literal(true)))

  private def PrefixNames(svr: MBeanServerConnection): Parser[actions.ManagedObjectNames] =
    (token("names") ^^^ actions.ManagedObjectNames(Map(unnamed -> ObjectName.WILDCARD), Field.literal(true))) |
    (token("names ") ~> MBeanQueryP(svr) map { case query => actions.ManagedObjectNames(query) })

  private def PostfixNames(svr: MBeanServerConnection, query: MBeanQuery): Parser[actions.ManagedObjectNames] =
    token("names") ^^^ actions.ManagedObjectNames(query)

  private def PrefixDescribe(svr: MBeanServerConnection): Parser[actions.DescribeMBeans] =
    token("describe ") ~> (flag("-d ") ~ MBeanQueryP(svr)) map {
      case detailed ~ query => actions.DescribeMBeans(query, detailed)
    }

  private def PostfixDescribe(svr: MBeanServerConnection, query: MBeanQuery): Parser[actions.DescribeMBeans] =
    (token("describe") ~> flag(" -d")) map {
      case detailed => actions.DescribeMBeans(query, detailed)
    }

  private def PrefixSelect(svr: MBeanServerConnection): Parser[actions.Query] =
    (SelectClause(svr, None) ~ (token(" from ") ~> MBeanQueryP(svr))) map {
      case projection ~ query => actions.Query(query, projection)
    }

  private def PostfixSelect(svr: MBeanServerConnection, query: MBeanQuery): Parser[actions.Query] =
    SelectClause(svr, Some(query)) map {
      case projection => actions.Query(query, projection)
    }

  private def SelectClause(svr: MBeanServerConnection, query: Option[MBeanQuery]): Parser[Projection] =
    (token("select ") ~> SpaceClass.* ~> JMXParsers.Projection(svr, query))

  private def PrefixSample(svr: MBeanServerConnection): Parser[actions.Sample] =
    (SampleClause(svr, None) ~ (token(" from ") ~> MBeanQueryP(svr)) ~ SampleTimingClause) map {
      case projection ~ query ~ timing => actions.Sample(actions.Query(query, projection), timing._1, timing._2)
    }

  private def PostfixSample(svr: MBeanServerConnection, query: MBeanQuery): Parser[actions.Sample] =
    (SampleClause(svr, Some(query)) ~ SampleTimingClause) map {
      case projection ~ timing => actions.Sample(actions.Query(query, projection), timing._1, timing._2)
    }

  private def SampleClause(svr: MBeanServerConnection, query: Option[MBeanQuery]): Parser[Projection] =
    (token("sample ") ~> SpaceClass.* ~> JMXParsers.Projection(svr, query))

  private def SampleTimingClause: Parser[(Int, Int)] =
    ((token(" every ") ~> NatBasic <~ token(" second" <~ 's'.?)).??(1) ~ (token(" for ") ~> NatBasic <~ token(" second" <~ 's'.?)).??(Int.MaxValue))

  private def PrefixInvoke(svr: MBeanServerConnection): Parser[actions.InvokeOperation] =
    token("invoke ") ~> JMXParsers.Invocation(svr, None) ~ (token(" on ") ~> MBeanQueryP(svr)) map {
      case ((opName, args)) ~ query => actions.InvokeOperation(query, opName, args)
    }

  private def PostfixInvoke(svr: MBeanServerConnection, query: MBeanQuery): Parser[actions.InvokeOperation] =
    (token("invoke ") ~> SpaceClass.* ~> JMXParsers.Invocation(svr, Some(query))) map {
      case opName ~ args => actions.InvokeOperation(query, opName, args)
    }

  private lazy val Disconnect: Parser[Action] =
    token("disconnect") ^^^ actions.Disconnect
}

