package cjmx.cli
package actions

object Exit extends Action {
  override def apply(context: ActionContext) =
    ActionResult(context.exit(context.lastStatusCode), Nil)
}

