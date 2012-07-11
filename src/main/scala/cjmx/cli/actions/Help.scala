package cjmx.cli
package actions

import scalaz._
import Scalaz._
import scalaz.effect._
import scalaz.iteratee._
import IterateeT._
import EnumeratorT._

import java.io.{BufferedReader, InputStreamReader}

import cjmx.util.MoreEnumerators._


case class Help(topic: Option[String]) extends Action {
  def apply(context: ActionContext) = {
    val resourceName = "/cjmx/help/%s.md".format(topic | "help")
    val resource = Option(getClass.getResourceAsStream(resourceName))
    val vResource = resource.toSuccess("No help for %s".format(topic | "")).toValidationNel
    for {
      resource <- vResource
      reader = new BufferedReader(new InputStreamReader(resource))
    } yield (context, enumIgnoringIoExceptions(enumLines[IO](reader)))
  }

}

