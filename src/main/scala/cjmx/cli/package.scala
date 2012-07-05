package cjmx

import scalaz._
import scalaz.ValidationNEL
import scalaz.syntax.validation._
import scalaz.effect.IO
import scalaz.iteratee._

import javax.management.remote.JMXConnector

import cjmx.util.jmx.JMX

package object cli extends JMX {
  type ActionResult = ValidationNEL[String, (ActionContext, EnumeratorT[String, IO])]
  type Action = ActionContext => ActionResult

  def enumMessageList(msgs: List[String]): EnumeratorT[String, IO] = EnumeratorT.enumList[String, IO](msgs)
  def enumMessages(msgs: String*): EnumeratorT[String, IO] = enumMessageList(msgs.toList)

  trait SimpleAction extends Action {
    final def apply(context: ActionContext) = act(context) map { msgs => (context, enumMessageList(msgs)) }
    def act(context: ActionContext): ValidationNEL[String, List[String]]
  }

  trait ConnectedAction extends Action {
    final def apply(context: ActionContext) =
      applyConnected(context, context.connectionState.asInstanceOf[Connected].connection)
    def applyConnected(context: ActionContext, connection: JMXConnector): ActionResult
  }

  trait SimpleConnectedAction extends ConnectedAction {
    final def applyConnected(context: ActionContext, connection: JMXConnector) =
      act(context, connection) map { msgs => (context, enumMessageList(msgs)) }
    def act(context: ActionContext, connection: JMXConnector): ValidationNEL[String, List[String]]
  }

  final object NoopAction extends Action {
    def apply(context: ActionContext) = (context, EnumeratorT.empty[String, IO]).success
  }
}
