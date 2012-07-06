package cjmx.cli

import sbt.complete.Parser


/** Provides generic parsers that add to the functionality provided by SBT complete. */
object MoreParsers {

  /**
   * Creates a `Parser[Seq[B]]` from a `A => Parser[(A, B)]`.
   *
   * This allows parsing of a `Seq[B]` where the parser for each `B` is dependent upon some previous parser state.
   * The parser used in each step produces a tuple whose first parameter is the next state and whose second
   * parameter is the parsed value.
   *
   * Note: this method is recursive but not tail recursive.
   */
  def repFlatMap[S, A](init: S)(p: S => Parser[(S, A)]): Parser[Seq[A]] = {
    def repFlatMapR[S, A](last: S, acc: Seq[A])(p: S => Parser[(S, A)]): Parser[Seq[A]] = {
      p(last).?.flatMap { more => more match {
        case Some((s, a)) => repFlatMapR(s, a +: acc)(p)
        case None => Parser.success(acc.reverse)
      } }
    }
    repFlatMapR[S, A](init, Seq.empty)(p)
  }
}

