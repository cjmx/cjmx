package cjmx.cli

trait SimpleAction extends Action {
  final def apply(context: ActionContext) = {
    val msgs = enumMessageSeq(act(context))
    (context.withStatusCode(0), msgs)
  }
  def act(context: ActionContext): Seq[String]
}

