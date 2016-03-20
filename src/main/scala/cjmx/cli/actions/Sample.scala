package cjmx.cli
package actions

import java.util.concurrent.{ Executors, LinkedBlockingQueue, TimeUnit }

// import scala.concurrent.duration._

case class Sample(query: Query, periodSeconds: Int, durationSeconds: Int) extends Action {
  def apply(context: ActionContext) = {
    @volatile var done = false

    val queue = new LinkedBlockingQueue[Option[String]]
    val scheduler = Executors.newScheduledThreadPool(1)

    val pollForDone = new Runnable {
      override def run = {
        while (!done && System.in.available > 0) {
          if (System.in.read == '\n') {
            queue.put(None)
            done = true
          }
        }
      }
    }
    scheduler.scheduleWithFixedDelay(pollForDone, 0, 100, TimeUnit.MILLISECONDS)

    val sample = new Runnable {
      override def run = {
        if (!done) {
          query(context).output.foreach { msg => queue.put(Some(msg)) }
        }
      }
    }
    scheduler.scheduleWithFixedDelay(sample, 0, periodSeconds, TimeUnit.SECONDS)

    val cancel = new Runnable {
      override def run = {
        queue.put(None)
        scheduler.shutdownNow()
      }
    }
    scheduler.schedule(cancel, durationSeconds, TimeUnit.SECONDS)

    val output: Iterator[String] = new Iterator[String] {
      private var end = false
      var next: String = null
      def hasNext = {
        if (end) false
        else queue.take() match {
          case Some(msg) => next = msg; true
          case None => end = true; false
        }
      }
    }

    ActionResult(context, output)
  }
}

