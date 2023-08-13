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

package cjmx.cli

import scala.collection.immutable.Seq

import sbt.internal.util.complete.Parser
import sbt.internal.util.complete.DefaultParsers.*

import javax.management.*

import cjmx.util.jmx.{MBeanQuery, JMX}
import JMXParsers.*

object Parsers:
  import Parser.*

  private lazy val GlobalActions: Parser[Action] = Exit | Help | SetFormat | Status

  private lazy val Exit: Parser[Action] =
    (token("exit") | token("done", _ => true)) ^^^ actions.Exit

  private lazy val Help: Parser[Action] =
    (token("help") ~> (' ' ~> any.+.string).?).map(topic => actions.Help(topic))

  private lazy val SetFormat: Parser[Action] =
    (token(
      "format "
    ) ~> (("text" ^^^ TextMessageFormatter) | "json" ^^^ JsonMessageFormatter.standard | "cjson" ^^^ JsonMessageFormatter.compact))
      .map(actions.SetFormat.apply)

  private lazy val Status: Parser[Action] =
    token("status") ^^^ actions.LastStatus

  def Disconnected(vms: Seq[JMX.VMID]): Parser[Action] =
    ListVMs | RemoteConnect | Connect(vms) | GlobalActions !!! "Invalid input"

  private val ListVMs: Parser[Action] =
    (token("list") | token("jps")) ^^^ actions.ListVMs

  private def VMID(vms: Seq[JMX.VMID]): Parser[String] =
    token(Digit.+.string.examples(vms.map(_.value)*))

  private def JMXUsername: Parser[String] = charClass(isScalaIDChar, "JMX username").*.string

  private def HostName: Parser[String] =
    token(chars(('-' :: 'a'.to('z').toList ::: 0.to(9).toList).mkString("")).*.string, "hostname")

  private def IpAddress: Parser[String] =
    token(chars(('.' :: 0.to(9).toList).mkString("")).*.string, "ip address")

  private def RemoteConnectionAddress(): Parser[((String, Int), Option[String])] =
    token(HostName | IpAddress, "hostname or address") ~ (token(":") ~> Port) ~ opt(
      token(token(' ') ~> JMXUsername)
    )

  private def QuietFlag: Parser[Boolean] = token(flag("-q "))

  private def Connect(vms: Seq[JMX.VMID]): Parser[actions.Connect] =
    (token("connect" ~> ' ') ~> (QuietFlag ~ VMID(vms))).map { case quiet ~ vmid =>
      actions.Connect(vmid, quiet)
    }

  private def RemoteConnect: Parser[actions.RemoteConnect] =
    (token("remote-connect" ~> ' ') ~> (QuietFlag ~ RemoteConnectionAddress())).map:
      case quiet ~ (host ~ port ~ username) => actions.RemoteConnect(host, port, username, quiet)

  def Connected(svr: MBeanServerConnection): Parser[Action] =
    MBeanAction(svr) | PrefixNames(svr) | PrefixDescribe(svr) | PrefixSelect(svr) | PrefixSample(
      svr
    ) | PrefixInvoke(svr) | Disconnect | GlobalActions !!! "Invalid input"

  def MBeanAction(svr: MBeanServerConnection): Parser[Action] =
    for
      _ <- token("mbeans ")
      query <- MBeanQueryP(svr) <~ SpaceClass.*
      action <- PostfixNames(svr, query) | PostfixSelect(svr, query) | PostfixSample(
        svr,
        query
      ) | PostfixDescribe(svr, query) | PostfixInvoke(svr, query)
    yield action

  private def MBeanQueryP(svr: MBeanServerConnection): Parser[MBeanQuery] =
    for
      name <- QuotedObjectNameParser(svr)
      query <- (token(" where ") ~> JMXParsers.QueryExpParser(svr, name)).?
    yield MBeanQuery(Some(name), query)

  private def PrefixNames(svr: MBeanServerConnection): Parser[actions.ManagedObjectNames] =
    (token("names") ^^^ actions.ManagedObjectNames(MBeanQuery.All)) |
      ((token("names ") ~> MBeanQueryP(svr)).map { case query =>
        actions.ManagedObjectNames(query)
      })

  private def PostfixNames(
      svr: MBeanServerConnection,
      query: MBeanQuery
  ): Parser[actions.ManagedObjectNames] =
    token("names") ^^^ actions.ManagedObjectNames(query)

  private def PrefixDescribe(svr: MBeanServerConnection): Parser[actions.DescribeMBeans] =
    (token("describe ") ~> (flag("-d ") ~ MBeanQueryP(svr))).map { case detailed ~ query =>
      actions.DescribeMBeans(query, detailed)
    }

  private def PostfixDescribe(
      svr: MBeanServerConnection,
      query: MBeanQuery
  ): Parser[actions.DescribeMBeans] =
    (token("describe") ~> flag(" -d")).map { case detailed =>
      actions.DescribeMBeans(query, detailed)
    }

  private def PrefixSelect(svr: MBeanServerConnection): Parser[actions.Query] =
    (SelectClause(svr, None) ~ (token(" from ") ~> MBeanQueryP(svr))).map:
      case projection ~ query => actions.Query(query, projection)

  private def PostfixSelect(svr: MBeanServerConnection, query: MBeanQuery): Parser[actions.Query] =
    SelectClause(svr, Some(query)).map { case projection =>
      actions.Query(query, projection)
    }

  private def SelectClause(
      svr: MBeanServerConnection,
      query: Option[MBeanQuery]
  ): Parser[Seq[Attribute] => Seq[Attribute]] =
    token("select ") ~> SpaceClass.* ~> JMXParsers.Projection(svr, query)

  private def PrefixSample(svr: MBeanServerConnection): Parser[actions.Sample] =
    (SampleClause(svr, None) ~ (token(" from ") ~> MBeanQueryP(svr)) ~ SampleTimingClause).map:
      case projection ~ query ~ timing =>
        actions.Sample(actions.Query(query, projection), timing._1, timing._2)

  private def PostfixSample(svr: MBeanServerConnection, query: MBeanQuery): Parser[actions.Sample] =
    (SampleClause(svr, Some(query)) ~ SampleTimingClause).map { case projection ~ timing =>
      actions.Sample(actions.Query(query, projection), timing._1, timing._2)
    }

  private def SampleClause(
      svr: MBeanServerConnection,
      query: Option[MBeanQuery]
  ): Parser[Seq[Attribute] => Seq[Attribute]] =
    token("sample ") ~> SpaceClass.* ~> JMXParsers.Projection(svr, query)

  private def SampleTimingClause: Parser[(Int, Int)] =
    (token(" every ") ~> NatBasic <~ token(" second" <~ 's'.?))
      .??(1) ~ (token(" for ") ~> NatBasic <~ token(" second" <~ 's'.?)).??(Int.MaxValue)

  private def PrefixInvoke(svr: MBeanServerConnection): Parser[actions.InvokeOperation] =
    (token("invoke ") ~> JMXParsers.Invocation(svr, None) ~ (token(" on ") ~> MBeanQueryP(svr)))
      .map { case ((opName, args)) ~ query =>
        actions.InvokeOperation(query, opName, args)
      }

  private def PostfixInvoke(
      svr: MBeanServerConnection,
      query: MBeanQuery
  ): Parser[actions.InvokeOperation] =
    (token("invoke ") ~> SpaceClass.* ~> JMXParsers.Invocation(svr, Some(query))).map:
      case opName ~ args => actions.InvokeOperation(query, opName, args)

  private lazy val Disconnect: Parser[Action] =
    token("disconnect") ^^^ actions.Disconnect
