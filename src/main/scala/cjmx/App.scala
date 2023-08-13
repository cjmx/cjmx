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

package cjmx

import scala.util.Try

import sbt.io.Path
import sbt.io.syntax.*
import sbt.internal.util.{FullReader, LineReader, Terminal}
import sbt.internal.util.complete.Parser

import cjmx.cli.REPL

object App:
  private val historyFile = (Path.userHome / ".cjmx.history").asFile

  def run(args: Array[String]): Int =
    val consoleReader = new FullReader(Some(historyFile), _: Parser[?], true, Terminal.console)
    val reader: Parser[?] => LineReader =
      if args.isEmpty then consoleReader
      else
        val firstArgAsConnect = Try(args.head.toInt).toOption.map(pid => "connect -q " + pid)
        firstArgAsConnect match
          case None =>
            val r = constReader(args :+ "exit")
            p => r
          case Some(cmd) =>
            if args.tail.isEmpty then prefixedReader(cmd +: args.tail, consoleReader)
            else
              val r = constReader(cmd +: args.tail :+ "exit")
              p => r
    REPL.run(reader, Console.out)

  private def constReader(args: Array[String]): LineReader =
    val iter = args.iterator
    new LineReader:
      override def readLine(prompt: String, mask: Option[Char]) =
        if iter.hasNext then Some(iter.next) else None

  private def prefixedReader(
      first: Array[String],
      next: Parser[?] => LineReader
  ): Parser[?] => LineReader =
    val firstReader = constReader(first)
    p =>
      new LineReader:
        override def readLine(prompt: String, mask: Option[Char]) =
          firstReader.readLine(prompt, mask).orElse(next(p).readLine(prompt, mask))
