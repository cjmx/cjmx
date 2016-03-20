package cjmx.cli
package actions

case object LastStatus extends SimpleAction {
  override def act(context: ActionContext) = {
    List(context.lastStatusCode.toString)
  }
}
