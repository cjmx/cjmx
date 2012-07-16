package cjmx.util

import scalaz._
import Scalaz._
import scalaz.effect._
import IoExceptionOr._
import scalaz.iteratee._
import Iteratee._

import java.io.{BufferedReader, IOException, StringReader}
import java.util.concurrent.{BlockingQueue, ArrayBlockingQueue, CountDownLatch, TimeUnit}

import org.scalatest._
import org.scalatest.matchers.ShouldMatchers


class MoreEnumeratorsTest extends FunSuite with ShouldMatchers {
  import MoreEnumerators._

  test("enumLines") {
    val reader = new BufferedReader(new StringReader((1 to 5).mkString("%n".format())))
    val lines =
      collect[String, List].up[IO] %=
      map[IoExceptionOr[String], String, IO]((_: IoExceptionOr[String]).toOption.getOrElse("exception")) &=
      enumLines[IO](reader)
    lines.run.unsafePerformIO should be (List("1", "2", "3", "4", "5"))
  }

  test("enumIgnoringIoExceptions") {
    val lines =
      collect[Int, List].up[IO] &=
      enumIgnoringIoExceptions[Int, IO](enumList[IoExceptionOr[Int], IO](List(
        ioExceptionOr(1),
        ioException(new IOException("")),
        ioExceptionOr(2),
        ioException(new IOException(""))
      )))
    lines.run.unsafePerformIO should be (List(1, 2))
  }

  test("enumBlockingQueue - natural termination") {
    val queue = new ArrayBlockingQueue[Signal[Int]](5)
    val thread = new Thread(new Runnable {
      override def run() {
        for (i <- 0 to 10) {
          queue.put(Value(i))
        }
        queue.put(Done)
      }
    })
    thread.start

    val iteratee = collect[Int, List] &= enumBlockingQueue[Int, Id](queue)
    iteratee.run should be (List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
  }

  test("enumBlockingQueue - early termination") {
    val queue = new ArrayBlockingQueue[Signal[Int]](3)
    val thread = new Thread(new Runnable {
      override def run() {
        while(true) {
          queue.put(Value(0))
        }
      }
    })
    thread.start

    val latch = new CountDownLatch(1)
    val iteratee = take[Int, List](5) &= enumBlockingQueue[Int, Id](queue, { thread.interrupt; latch.countDown() })
    iteratee.run should be (List(0, 0, 0, 0, 0))
    latch.await(2, TimeUnit.SECONDS)
  }
}
