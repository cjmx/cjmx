package cjmx.cli

import java.lang.management.ManagementFactory
import javax.management.{MalformedObjectNameException, ObjectName}

import sbt.complete.Parser

import org.scalatest._
import org.scalatest.matchers._

import ObjectNameParser._


class ObjectNameParserTest extends FunSuite with ShouldMatchers {

  val validExamples = Seq(
    // Basic examples
    "java.lang:*",
    "java.lang:type=Memory",
    "java.lang:type=Memory,*",
    "java.lang:type=*",
    "java.lang:type=MemoryPool,name=*,*",

    // Quoting
    """Domain:type="foo"""",
    """Domain:type="foo",*""",
    """Domain:type="f\"oo",*""",

    // Escaping
    "Domain:type=a\\*b",
    "Domain:type=a\\*b,*",
    "Domain:type=a\\?b,*",

    // From JMX specification
    "*:*",
    ":*",
    "??Domain:*",
    "*Domain*:*",
    "*:description=Printer,type=laser,*",
    "*Domain:description=Printer,*",
    "*Domain:description=P*,*"
  )

  validExamples foreach { ex =>
    test("valid - " + ex) {
      parse(ex) should be (Right(new ObjectName(ex)))
    }
  }

  val invalidExamples = Seq(
    "Domain:desc*=*,*", // wildcard not allowed in key
    """Domain:"desc""" // unclosed quoted key
  )

  invalidExamples foreach { ex =>
    test("invalid - " + ex) {
      // sanity check example is invalid
      evaluating { new ObjectName(ex) } should produce[MalformedObjectNameException]
      parse(ex) should be ('left)
    }
  }

  def parse(str: String): Either[String, ObjectName] =
    Parser.parse(str, JmxObjectName(ManagementFactory.getPlatformMBeanServer))
}
