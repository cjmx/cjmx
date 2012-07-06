package cjmx.cli
package actions

import scalaz.effect.IO
import scalaz.iteratee.EnumeratorT
import scalaz.syntax.validation._


object Exit extends Action {
  override def apply(context: ActionContext) = {
    (context.exit(0), EnumeratorT.empty[String, IO]).success
  }
}

