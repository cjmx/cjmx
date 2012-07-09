package cjmx.cli

import scala.collection.JavaConverters._

import scalaz.syntax.show._

import java.lang.management.ManagementFactory
import javax.management._
import javax.management.openmbean.{CompositeData, CompositeDataSupport, CompositeType, OpenType, SimpleType}

import sbt.complete.Parser

import org.scalatest._
import org.scalatest.matchers._

import cjmx.util.jmx.JMXTags
import cjmx.util.jmx.JMX._


class ProjectionParserTest extends FunSuite with ShouldMatchers {

  // Assumes HeapMemoryUsage.{init = 0, committed=1000000, used=500000, max=2000000}
  val validExamplesBasedOnMemoryMXBean = Seq(
    "HeapMemoryUsage" -> Seq("HeapMemoryUsage:", "committed: 1000000", "init: 0", "max: 2000000", "used: 500000"),
    "HeapMemoryUsage.init" -> Seq("HeapMemoryUsage.init: 0"),
    "HeapMemoryUsage.used, HeapMemoryUsage.max" -> Seq("HeapMemoryUsage.used: 500000", "HeapMemoryUsage.max: 2000000"),
    "HeapMemoryUsage.used as used, HeapMemoryUsage.max as max" -> Seq("used: 500000", "max: 2000000")
  )

  validExamplesBasedOnMemoryMXBean foreach { case (ex, expectedOutput) =>
    test("valid - " + ex) {
      val attrs = Seq(heapMemoryUsage(init = 0, committed = 1000000, used = 500000, max = 2000000))
      val result = parse(ex)
      val projected = result.right.get(attrs)
      val projectedAsStrings = projected.flatMap { _.shows.split("%n".format()).map { _.trim } }
      projectedAsStrings should be (expectedOutput)
    }
  }

  private def parse(str: String): Either[String, Seq[Attribute] => Seq[Attribute]] =
    Parser.parse(str, JMXParsers.Projection(ManagementFactory.getPlatformMBeanServer, new ObjectName("java.lang:type=Memory"), None))

  private def heapMemoryUsage(init: Long, committed: Long, used: Long, max: Long): Attribute =
    new Attribute("HeapMemoryUsage", memoryComposite(init, committed, used, max))

  private def memoryComposite(init: Long, committed: Long, used: Long, max: Long): CompositeData = {
    new CompositeDataSupport(
      new CompositeType("typeName", "desc",
        Array("init", "committed", "used", "max"),
        Array("init", "committed", "used", "max"),
        Array[OpenType[_]](SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG)
      ),
      Map("init" -> 0L, "committed" -> 1000000L, "used" -> 500000L, "max" -> 2000000L).asJava
    )
  }
}

