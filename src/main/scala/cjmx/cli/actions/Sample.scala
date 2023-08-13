/*
 * Copyright (c) 2012, cjmx
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package cjmx.cli
package actions

import java.util.concurrent.{Executors, LinkedBlockingQueue, TimeUnit}

// import scala.concurrent.duration._

case class Sample(query: Query, periodSeconds: Int, durationSeconds: Int) extends Action {
  def apply(context: ActionContext) = {
    @volatile var done = false

    val queue = new LinkedBlockingQueue[Option[String]]
    val scheduler = Executors.newScheduledThreadPool(1)

    val pollForDone = new Runnable {
      override def run =
        while (!done && System.in.available > 0)
          if (System.in.read == '\n') {
            queue.put(None)
            done = true
          }
    }
    scheduler.scheduleWithFixedDelay(pollForDone, 0, 100, TimeUnit.MILLISECONDS)

    val sample = new Runnable {
      override def run =
        if (!done) {
          query(context).output.foreach(msg => queue.put(Some(msg)))
        }
    }
    scheduler.scheduleWithFixedDelay(sample, 0, periodSeconds.toLong, TimeUnit.SECONDS)

    val cancel = new Runnable {
      override def run = {
        queue.put(None)
        scheduler.shutdownNow()
        ()
      }
    }
    scheduler.schedule(cancel, durationSeconds.toLong, TimeUnit.SECONDS)

    val output: Iterator[String] = new Iterator[String] {
      private var end = false
      var next: String = null
      def hasNext =
        if (end) false
        else
          queue.take() match {
            case Some(msg) => next = msg; true
            case None      => end = true; false
          }
    }

    ActionResult(context, output)
  }
}
