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
}
