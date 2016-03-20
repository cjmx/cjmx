package cjmx.cli

import org.scalatest._

import sbt.complete.Parser
import cjmx.util.jmx.JMXConnection


class ExampleEmbedding extends FunSuite with Matchers {

  test("Example of running mbean actions against current jvm") {
    val result = runMBeanAction("mbeans 'java.lang:type=Memory' select *").fold(e => Vector(e), identity)
    result(0) should be ("java.lang:type=Memory")
  }


  def runMBeanAction(str: String): Either[String, Vector[String]] = {
    val cnx = JMXConnection.PlatformMBeanServerConnection
    for {
      action <- Parser.parse(str, Parsers.MBeanAction(cnx.mbeanServer)).right
      msgs <- {
        val initialCtx = ActionContext.embedded(connectionState = ConnectionState.Connected(cnx))
        val ActionResult(ctx, msgs) = action(initialCtx)
        if (ctx.lastStatusCode != 0)
          Left("Action failed with status code: " + ctx.lastStatusCode)
        else
          Right(msgs)
      }.right
    } yield msgs.toVector
  }
}
