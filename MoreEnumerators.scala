package cjmx.util

import scalaz._
import Scalaz._
import scalaz.effect._
import scalaz.iteratee._

import java.io.{BufferedReader, InputStreamReader}


object MoreEnumerators {
  def enumLines[F[_]](r: => BufferedReader)(implicit MO: MonadPartialOrder[F, IO]): EnumeratorT[IoExceptionOr[String], F] =
    new EnumeratorT[IoExceptionOr[String], F] {
      import MO._
      import EnumeratorT._
      lazy val reader = r
      def apply[A] = (s: StepT[IoExceptionOr[String], F, A]) =>
        s.mapContOr({
          k => {
            val i = IoExceptionOr(reader.readLine)
            if (i exists { _ != null }) k(Input(i)) >>== apply[A]
            else s.pointI
          }
        }, { IoExceptionOr(reader.close()); s.pointI })
    }

  def enumIgnoringIoExceptions[A, F[_]](e: EnumeratorT[IoExceptionOr[A], F])(implicit M: Monad[F]): EnumeratorT[A, F] = {
    e flatMap { i => i.toOption.fold(
      ii => ii.point[({type l[a] = EnumeratorT[a, F]})#l],
      implicitly[Monoid[EnumeratorT[A, F]]].zero
    ) }
  }
}
