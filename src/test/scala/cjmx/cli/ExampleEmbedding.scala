package cjmx.cli

import scalaz.\/
import scalaz.Free.Trampoline
import scalaz.std.vector._
//import scalaz.syntax.either._
import scalaz.syntax.id._
import scalaz.syntax.std.either._

import org.scalatest._

import sbt.complete.Parser
import cjmx.util.jmx.JMXConnection


class ExampleEmbedding extends FunSuite with Matchers {

  test("Example of running mbean actions against current jvm") {
    val result = runMBeanAction("mbeans 'java.lang:type=Memory' select *").fold(e => Vector(e), identity)
    result(0) should be ("java.lang:type=Memory")
  }

  def runMBeanAction(str: String): String \/ Vector[String] = for {
    cnx <- JMXConnection.PlatformMBeanServerConnection.right
    action <- Parser.parse(str, Parsers.MBeanAction(cnx.mbeanServer)).disjunction
    msgEnum <- {
      val initialCtx = ActionContext(connectionState = Connected(cnx))
      val (ctx, msgs) = action(initialCtx)
      if (ctx.lastStatusCode != 0)
        ("Action failed with status code: " + ctx.lastStatusCode).left
      else
        msgs.right
    }
  } yield msgEnum.chunkAll.runLastOr(Vector()).run
}
