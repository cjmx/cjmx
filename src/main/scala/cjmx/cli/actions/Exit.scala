package cjmx.cli
package actions

import scalaz.stream.Process

object Exit extends Action {
  override def apply(context: ActionContext) =
    Process.emitO(None)
}

