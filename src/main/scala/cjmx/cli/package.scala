package cjmx

import scalaz._
import scalaz.syntax.id._
import scalaz.effect.IO
import scalaz.iteratee._

import cjmx.util.jmx.{JMX, JMXConnection}

package object cli extends JMX {
  type ActionResult = (ActionContext, EnumeratorT[String, IO])
  type Action = ActionContext => ActionResult

  def enumMessageList(msgs: List[String]): EnumeratorT[String, IO] = EnumeratorT.enumList[String, IO](msgs)
  def enumMessageSeq(msgs: Seq[String]): EnumeratorT[String, IO] = enumMessageList(msgs.toList)
  def enumMessages(msgs: String*): EnumeratorT[String, IO] = enumMessageList(msgs.toList)

  trait SimpleAction extends Action {
    final def apply(context: ActionContext) = act(context) |> enumMessageSeq |> { msgs => (context.withStatusCode(0), msgs) }
    def act(context: ActionContext): Seq[String]
  }

  trait ConnectedAction extends Action {
    final def apply(context: ActionContext) =
      applyConnected(context, context.connectionState.asInstanceOf[Connected].connection)
    def applyConnected(context: ActionContext, connection: JMXConnection): ActionResult
  }

  trait SimpleConnectedAction extends ConnectedAction {
    final def applyConnected(context: ActionContext, connection: JMXConnection) =
      act(context, connection) |> enumMessageSeq |> { msgs => (context.withStatusCode(0), msgs) }
    def act(context: ActionContext, connection: JMXConnection): Seq[String]
  }

  final object NoopAction extends Action {
    def apply(context: ActionContext) = (context, EnumeratorT.empty[String, IO])
  }
}
