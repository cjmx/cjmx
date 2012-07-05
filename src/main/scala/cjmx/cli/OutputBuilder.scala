package cjmx.cli

import scala.collection.mutable.ListBuffer

import scalaz._
import Scalaz._


final class OutputBuilder {
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

  private def indentedStr(s: String) = (" " multiply (indentation * 2)) + s

  def lines: List[String] = _lines.toList
}

