package cjmx.cli

import java.lang.management.ManagementFactory
import javax.management.{MalformedObjectNameException, ObjectName}

import sbt.complete.Parser

import org.scalatest._
import org.scalatest.matchers._

import JMXParsers._


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
    """Domain:type="foo,bar",*""",
    """Domain:type="foo:bar",*""",
    """Domain:type="foo=bar",*""",
    """Domain:type="foo?bar",*""",
    """Domain:type="foo\?bar",*""",
    """Domain:type="foo*bar",*""",
    """Domain:type="foo\*bar",*""",

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
    """Domain:"desc""", // unclosed quoted key
    ":key=v1,key=v2" // duplicate key
  )

  invalidExamples foreach { ex =>
    test("invalid - " + ex) {
      // sanity check example is invalid
      evaluating { new ObjectName(ex) } should produce[MalformedObjectNameException]
      parse(ex) should be ('left)
    }
  }

  def parse(str: String): Either[String, ObjectName] =
    Parser.parse(str, ObjectNameParser(ManagementFactory.getPlatformMBeanServer))

  val completionExamples = Seq(
    ("java.lang:", Set("*", "<key>=", "name=", "type=")),
    ("java.lang:type=", Set("*", "<value>", "ClassLoading", "Compilation", "GarbageCollector", "Memory", "MemoryManager", "MemoryPool", "OperatingSystem", "Runtime", "Threading")),
    ("java.lang:type=M", Set("Memory", "MemoryManager", "MemoryPool")),
    ("java.lang:type=MemoryPool,", Set("*", "<key>=", "name=")),
    ("java.lang:type=MemoryPool,name=", Set("*", "<value>", "Code Cache", "CMS Old Gen", "CMS Perm Gen", "Par Eden Space", "Par Survivor Space")),
    ("java.lang:type=MemoryPool,name=Code Cache,", Set("*", "<key>="))
  )

  completionExamples foreach { case (str, expected) =>
    test("completion - " + str) {
      completions(str) should be (expected)
    }
  }

  def completions(str: String): Set[String] =
    Parser.completions(ObjectNameParser(ManagementFactory.getPlatformMBeanServer), str, Int.MaxValue).get.map { _.display }
}
