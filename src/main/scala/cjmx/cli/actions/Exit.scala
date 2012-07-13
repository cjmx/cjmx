package cjmx.cli
package actions

import scalaz.iteratee.EnumeratorT


object Exit extends Action {
  override def apply(context: ActionContext) = {
    (context.exit(context.lastStatusCode), EnumeratorT.empty)
  }
}

