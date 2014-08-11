package cjmx.cli
package actions

import scala.concurrent.duration._
import scalaz.{\/-, -\/}
import scalaz.concurrent.Task
import scalaz.stream.{ DefaultScheduler, Process, wye }
import scalaz.stream.async.signal

case class Sample(query: Query, periodSeconds: Int, durationSeconds: Int) extends Action {
  def apply(context: ActionContext) = {
    implicit val scheduler = DefaultScheduler
    val r = Process.suspend {
      val cancel = signal[Boolean]
      cancel.set(false).run
      @volatile var done = false
      val th = new Thread {
        override def run = {
          while (!done) {
            while (!done && System.in.available > 0) {
              if (System.in.read == '\n') {
                cancel.set(true).run
                done = true
              }
            }
            Thread.sleep(100)
          }
        }
      }
      th.start
      val canceled = cancel.discrete
      val beat = Process.awakeEvery(periodSeconds.seconds).takeWhile(_.toSeconds <= durationSeconds)
      val sample = beat flatMap { _ => query(context)._2 }
      canceled.wye(sample)(wye.interrupt).onComplete { done = true; Process.halt }
    }
    (context, r)
  }
}

