package cjmx.cli
package actions

import cjmx.util.MoreEnumerators._

import java.io.{BufferedReader, InputStreamReader}

import scalaz._
import Scalaz._
import scalaz.stream.Process

case class Help(topic: Option[String]) extends Action {
  def apply(context: ActionContext) = {
    val resourceName = "/cjmx/help/%s.md".format(topic | "help")
    val resource = Option(getClass.getResourceAsStream(resourceName))
    val vResource = resource.toRightDisjunction("No help for %s".format(topic | ""))
    vResource fold (
      err => (context.withStatusCode(1), Process.emit(err)),
      resource => (context.withStatusCode(0),
                   linesR(new BufferedReader(new InputStreamReader(resource))))
    )
  }
}

