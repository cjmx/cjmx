package cjmx.util

import scalaz._
import Scalaz._
import scalaz.effect._
import scalaz.concurrent.Task
import IoExceptionOr._
import scalaz.stream._

import java.io.{BufferedReader, IOException, StringReader}
import java.util.concurrent.{BlockingQueue, ArrayBlockingQueue, CountDownLatch, TimeUnit}

import org.scalatest._


class MoreEnumeratorsTest extends FunSuite with Matchers {
  import MoreEnumerators._

  test("enumLines") {
    println("enumerating lines")
    val reader = new BufferedReader(new StringReader((1 to 5).mkString("%n".format())))
    val lines = linesR(reader)
    lines.chunkAll.runLastOr(Vector()).run should be (Vector("1", "2", "3", "4", "5"))
  }

  test("enumBlockingQueue - natural termination") {
    val queue = new ArrayBlockingQueue[Signal[Int]](5)
    Task {
      for (i <- 0 to 10) queue.put(Value(i))
      queue.put(Done)
    } runAsync { _ => () }

    enumBlockingQueue(queue).runLog.run.toList should be (List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
  }

  test("enumBlockingQueue - early termination") {
    val queue = new ArrayBlockingQueue[Signal[Int]](3)
    @volatile var ok = true
    Task {
      while(ok) queue.put(Value(0))
    } runAsync { _ => () }

    val latch = new CountDownLatch(1)
    val q = enumBlockingQueue(queue, { latch.countDown; ok = false })
    q.take(5).runLog.run.toList should be (List(0, 0, 0, 0, 0))
    latch.await(2, TimeUnit.SECONDS)
  }
}
