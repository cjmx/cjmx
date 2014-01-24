package cjmx.cli
package actions

import scalaz.stream.Process

case class SetFormat(formatter: MessageFormatter) extends Action {

  override def apply(context: ActionContext) =
    updateContext(ActionContext.Format(formatter))
}
