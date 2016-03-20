import language.implicitConversions

package object cjmx {
  implicit def rightBiasEither[A, B](e: Either[A, B]): Either.RightProjection[A, B] =
    e.right
}
