package cjmx.cli

import java.lang.management.ManagementFactory
import javax.management._

import sbt.complete.Parser

import org.scalatest._


class InvocationParserTest extends FunSuite with ShouldMatchers {

  val validExamples = Seq(
    "gc()" -> ("gc", Seq.empty[AnyRef]),
    "getThreadInfo(51235L)" -> ("getThreadInfo", Seq(51235L)),
    "getThreadInfo(51235)" -> ("getThreadInfo", Seq(51235)),
    "getThreadInfo({51235L, 11011L})" -> ("getThreadInfo", Seq(Seq(51235L, 11011L))),
    "getLoggerLevel('cjmx')" -> ("getLoggerLevel", Seq("cjmx")),
    "setLoggerLevel('cjmx', 'DEBUG')" -> ("setLoggerLevel", Seq("cjmx", "DEBUG"))
  )

  validExamples foreach { case (ex, (expectedName, expectedArgs)) =>
    test("valid - " + ex) {
      val result = parse(ex)
      result.right.map { _._1 } should be (Right(expectedName))
      result.right.map { _._2.collect {
        case a: Array[_] => a.toSeq
        case o => o
      }} should be (Right(expectedArgs))
    }
  }

  private def parse(str: String): Either[String, (String, Seq[AnyRef])] =
    Parser.parse(str, JMXParsers.Invocation(ManagementFactory.getPlatformMBeanServer, None))
}

