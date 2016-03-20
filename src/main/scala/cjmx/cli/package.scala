package cjmx

package object cli {
  type Action = ActionContext => ActionResult

  object NoopAction extends Action {
    def apply(context: ActionContext) = ActionResult(context, Nil)
  }
}
