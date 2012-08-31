package cjmx.cli
package actions

import scalaz.iteratee.EnumeratorT


case class SetFormat(formatter: MessageFormatter) extends Action {

  override def apply(context: ActionContext) = {
    (context.withFormatter(formatter), EnumeratorT.empty)
  }

}
