package cjmx.cli

import scalaz.\/
import scalaz.\/._
import scalaz.concurrent.Task
import scalaz.stream.Process

import org.scalatest._

import sbt.complete.Parser
import cjmx.util.jmx.JMX


class ExampleEmbedding extends FunSuite with Matchers {

  test("Example of running mbean actions against current jvm") {
    val result = runMBeanAction("mbeans 'java.lang:type=Memory' select *").fold(e => Vector(e), identity)
    result(0) should be ("java.lang:type=Memory")
  }

  def runMBeanAction(str: String): String \/ Vector[String] = for {
    cnx <- right(JMX.PlatformMBeanServerConnection)
    action <- fromEither(Parser.parse(str, Parsers.MBeanAction(cnx.mbeanServer)))
    msgs <- {
      val initialCtx = ActionContext(connectionState = Some(cnx))
      val msgs: Process[Task,String] = action(initialCtx).stripW.takeWhile(_.nonEmpty).map(_.get)
      msgs.chunkAll.runLastOr(Vector()).attemptRun.leftMap(err => "Action failed due to: " + err)
    }
  } yield msgs
}
