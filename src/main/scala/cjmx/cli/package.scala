package cjmx

import scalaz._
import scalaz.syntax.id._
import scalaz.Free.Trampoline
import scalaz.concurrent.Task
import scalaz.stream.Process

import cjmx.util.jmx.{JMX, JMXConnection}

package object cli extends JMX {
  /** A stream of `A` values. */
  type Source[+A] = Process[Task,A]
  type ActionResult = (ActionContext, Source[String])
  type Action = ActionContext => ActionResult

  def enumMessageList(msgs: List[String]): Source[String] =
    Process.emitAll(msgs)

  def enumMessageSeq(msgs: Seq[String]): Source[String] =
    Process.emitAll(msgs)

  def enumMessages(msgs: String*): Source[String] =
    Process.emitAll(msgs)

  trait SimpleAction extends Action {
    final def apply(context: ActionContext) = {
      val msgs = enumMessageSeq(act(context))
      (context.withStatusCode(0), msgs)
    }
    def act(context: ActionContext): Seq[String]
  }

  trait ConnectedAction extends Action {
    final def apply(context: ActionContext) =
      applyConnected(context, context.connectionState.asInstanceOf[Connected].connection)
    def applyConnected(context: ActionContext, connection: JMXConnection): ActionResult
  }

  trait SimpleConnectedAction extends ConnectedAction {
    final def applyConnected(context: ActionContext, connection: JMXConnection) = {
      val msgs = enumMessageSeq(act(context, connection))
      (context.withStatusCode(0), msgs)
    }
    def act(context: ActionContext, connection: JMXConnection): Seq[String]
  }

  final object NoopAction extends Action {
    def apply(context: ActionContext) = (context, Process.halt)
  }
}
