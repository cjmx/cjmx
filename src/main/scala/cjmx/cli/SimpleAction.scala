package cjmx.cli

import scala.collection.immutable.Seq

trait SimpleAction extends Action {
  final def apply(context: ActionContext) = {
    ActionResult(context.withStatusCode(0), act(context))
  }
  def act(context: ActionContext): Seq[String]
}

