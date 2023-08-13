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

import sbt.internal.util.complete.Parser
import sbt.internal.util.complete.DefaultParsers.SpaceClass


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
    def repFlatMapR(last: S, acc: Seq[A])(p: S => Parser[(S, A)]): Parser[Seq[A]] = {
      p(last).?.flatMap { more => more match {
        case Some((s, a)) => repFlatMapR(s, a +: acc)(p)
        case None => Parser.success(acc.reverse)
      } }
    }
    repFlatMapR(init, Seq.empty)(p)
  }

  def ws = SpaceClass
}

