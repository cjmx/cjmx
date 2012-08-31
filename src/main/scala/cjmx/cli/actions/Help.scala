package cjmx.cli
package actions

import scalaz._
import Scalaz._
import scalaz.iteratee._
import IterateeT._
import EnumeratorT._

import java.io.{BufferedReader, InputStreamReader}

import cjmx.util.MoreEnumerators._


case class Help(topic: Option[String]) extends Action {
  def apply(context: ActionContext) = {
    val resourceName = "/cjmx/help/%s.md".format(topic | "help")
    val resource = Option(getClass.getResourceAsStream(resourceName))
    val vResource = resource.toRightDisjunction("No help for %s".format(topic | ""))
    vResource fold (
      err => (context.withStatusCode(1), enumOne(err)),
      resource => (context.withStatusCode(0), enumIgnoringIoExceptions(enumLines(new BufferedReader(new InputStreamReader(resource)))))
    )
  }
}

