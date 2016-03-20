package cjmx.cli
package actions

case class SetFormat(formatter: MessageFormatter) extends Action {

  override def apply(context: ActionContext) =
    ActionResult(context.withFormatter(formatter), Nil)
}
