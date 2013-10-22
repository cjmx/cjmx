package cjmx.cli
package actions

import scala.collection.JavaConverters._
import scalaz.std.AllInstances._
import scalaz.syntax.show._

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit._
import java.util.concurrent.atomic.AtomicBoolean

import javax.management.Attribute
import javax.management.remote.JMXConnector

import cjmx.util.MoreEnumerators._
import cjmx.util.jmx._


case class Sample(query: Query, periodSeconds: Int, durationSeconds: Int) extends Action {
  private val periodMillis = MILLISECONDS.convert(periodSeconds, SECONDS)
  private val durationMillis = MILLISECONDS.convert(durationSeconds, SECONDS)

  def apply(context: ActionContext) = {

    val queue = new ArrayBlockingQueue[Signal[String]](1024)
    val stop = new AtomicBoolean
    val sampler = new Thread(new Runnable {
      override def run() {
        try {
          val started = System.currentTimeMillis
          var finished = started + durationMillis
          var running = started
          while (!stop.get && running < finished) {
            val (_, enum) = query(context)
            val strs = new collection.mutable.ListBuffer[String]
            enum.to(scalaz.stream.io.fillBuffer(strs)).run.run
            strs foreach { s => queue.put(Value(s)) }
            strs.clear
            running += periodMillis
            try Thread.sleep(running - System.currentTimeMillis)
            catch {
              case e: InterruptedException => // Ignore
           }
          }
          queue.put(Done)
        } catch {
          case e: InterruptedException => // Ignore
        }
      }
    })
    def stopSampler() = { stop.set(true); sampler.interrupt() }

    val canceller = new Thread(new Runnable {
      override def run() {
        try {
          while (!stop.get && System.in.read != '\n') {}
          stopSampler()
        } catch {
          case e: InterruptedException => // Ignore
        }
      }
    })

    sampler.start()
    canceller.start()
    (context, enumBlockingQueue(queue, { stopSampler(); canceller.interrupt() }))
  }
}

