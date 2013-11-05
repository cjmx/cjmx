package cjmx

import scalaz._
import scalaz.syntax.id._
import scalaz.Free.Trampoline
import scalaz.concurrent.Task
import scalaz.stream.{Writer,Process,Process1}

import cjmx.util.jmx.{JMX, JMXConnection}

package object cli extends JMX {
  /**
   * An action may read and write the formatter and connection state,
   * but its status code is derived from whether it finishes
   * successfully.
   */
  type ActionResult = Writer[Task, ActionContext.Delta, Option[String]]
  type Action = ActionContext => ActionResult

  def emitMessageSeq(msgs: Seq[String]): ActionResult =
    Process.liftW(Process.emitAll(msgs.map(Some(_))))

  def emitMessages(msgs: String*): ActionResult =
    emitMessageSeq(msgs)

  def emitMessages(s: Process[Task,String]): ActionResult =
    Process.liftW(s.map(Some(_)))

  def updateContext(d: ActionContext.Delta): ActionResult =
    Process.tell(d)

  def fail(msg: String): ActionResult =
    Process.fail(ActionFailed(msg))

  trait SimpleAction extends Action {
    final def apply(context: ActionContext) = emitMessageSeq(act(context))
    def act(context: ActionContext): Seq[String]
  }

  trait ConnectedAction extends Action {
    final def apply(context: ActionContext) =
      context.connectionState
             .map(cnx => applyConnected(context, cnx))
             .getOrElse(fail("action requires a connection"))

    def applyConnected(context: ActionContext, connection: JMXConnection): ActionResult
  }

  trait SimpleConnectedAction extends ConnectedAction {
    final def applyConnected(context: ActionContext, connection: JMXConnection) =
      emitMessageSeq(act(context, connection))
    def act(context: ActionContext, connection: JMXConnection): Seq[String]
  }

  final object NoopAction extends Action {
    def apply(context: ActionContext) = Process.halt
  }
}
