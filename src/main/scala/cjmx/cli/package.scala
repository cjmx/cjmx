package cjmx

import scalaz._
import scalaz.syntax.id._
import scalaz.Free.Trampoline
import scalaz.concurrent.Task
import scalaz.stream.Process

import cjmx.util.jmx.JMX

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

  final object NoopAction extends Action {
    def apply(context: ActionContext) = (context, Process.halt)
  }
}
