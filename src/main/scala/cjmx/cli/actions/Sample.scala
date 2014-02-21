package cjmx.cli
package actions

import scala.concurrent.duration._
import scalaz.stream.Process

case class Sample(query: Query, periodSeconds: Int, durationSeconds: Int) extends Action {
  def apply(context: ActionContext) = {
    val r = query(context)._2 ++
      Process.awakeEvery(periodSeconds.seconds)
        .takeWhile(_.toSeconds <= durationSeconds)
        .flatMap { _ => query(context)._2 }
    (context, r)
  }
}

