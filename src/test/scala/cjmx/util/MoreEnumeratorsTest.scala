package cjmx.util

import scalaz._
import Scalaz._
import scalaz.effect._
import IoExceptionOr._
import scalaz.iteratee._
import Iteratee._

import java.io.{BufferedReader, IOException, StringReader}

import org.scalatest._
import org.scalatest.matchers.ShouldMatchers


class MoreEnumeratorsTest extends FunSuite with ShouldMatchers {
  import MoreEnumerators._

  private val reader = new BufferedReader(new StringReader((1 to 5).mkString("%n".format())))

  test("enumLines") {
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
}
