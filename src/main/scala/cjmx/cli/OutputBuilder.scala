package cjmx.cli

import scala.collection.mutable.ListBuffer

final class OutputBuilder {
  private val newline = "%n".format()
  private val _lines = ListBuffer[String]()
  private var indentation = 0

  def indent() { indentation += 1 }
  def outdent() { indentation = (indentation - 1) max 0 }

  def <+(ln: String) { _lines += indentedStr(ln) }
  def <++(lns: Traversable[String]) { _lines ++= (lns map indentedStr) }

  def indented(f: => Any) {
    indent()
    try f
    finally outdent()
  }

  private def indentedStr(s: String) = s.split(newline).map { l => (" " * (indentation * 2)) + l }.mkString(newline)

  def lines: List[String] = _lines.toList
}

