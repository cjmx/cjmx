package cjmx

import scalaz.ValidationNEL
import scalaz.syntax.validation._

import javax.management.remote.JMXConnector

package object cli {
  type ActionResult = ValidationNEL[String, (ActionContext, Seq[String])]
  type Action = ActionContext => ActionResult

  trait SimpleAction extends Action {
    final def apply(context: ActionContext) = act(context) map { msgs => (context, msgs) }
    def act(context: ActionContext): ValidationNEL[String, Seq[String]]
  }

  trait ConnectedAction extends Action {
    final def apply(context: ActionContext) =
      applyConnected(context, context.connectionState.asInstanceOf[Connected].connection)
    def applyConnected(context: ActionContext, connection: JMXConnector): ActionResult
  }

  trait SimpleConnectedAction extends ConnectedAction {
    final def applyConnected(context: ActionContext, connection: JMXConnector) =
      act(context, connection) map { msgs => (context, msgs) }
    def act(context: ActionContext, connection: JMXConnector): ValidationNEL[String, Seq[String]]
  }

  final object NoopAction extends Action {
    def apply(context: ActionContext) = (context, Seq.empty).success
  }
}
