package cjmx.cli
package actions

import scala.concurrent.duration._
import scalaz.stream.Process

case class Sample(query: Query, periodSeconds: Int, durationSeconds: Int) extends Action {
  def apply(context: ActionContext) =
    query(context) ++
    Process.awakeEvery(periodSeconds seconds)
           .takeWhile(_.toSeconds <= durationSeconds)
           .flatMap { _ => query(context) }
}

