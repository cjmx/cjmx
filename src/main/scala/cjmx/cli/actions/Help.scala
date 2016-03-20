package cjmx.cli
package actions

import scala.collection.JavaConverters._

import java.io.{ BufferedReader, InputStreamReader }

case class Help(topic: Option[String]) extends Action {
  def apply(context: ActionContext) = {
    val resourceName = "/cjmx/help/%s.md".format(topic.getOrElse("help"))
    val resource = Option(getClass.getResourceAsStream(resourceName))
    val vResource = resource.toRight("No help for %s".format(topic.getOrElse("")))
    vResource match {
      case Left(err) =>
        ActionResult(context.withStatusCode(1), List(err))
      case Right(resource) =>
        val lines: List[String] = {
          val reader = new BufferedReader(new InputStreamReader(resource))
          try {
            reader.lines.collect(java.util.stream.Collectors.toList[String]).asScala.toList
          } finally {
            reader.close()
          }
        }
        ActionResult(context.withStatusCode(0), lines)
    }
  }
}

