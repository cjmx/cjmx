package cjmx
package cli

case class ActionResult(context: ActionContext, output: Iterator[String])

object ActionResult {
  def apply(context: ActionContext, output: Iterable[String]): ActionResult =
    ActionResult(context, output.iterator)
}
