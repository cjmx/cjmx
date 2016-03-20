package cjmx.cli

import cjmx.util.jmx.JMX._

sealed abstract class InvocationResult

object InvocationResult {
  final case class Succeeded(value: JValue) extends InvocationResult {
    override def toString = value.toString
  }
  final case class Failed(cause: Exception) extends InvocationResult {
    override def toString = "exception: " + cause.getMessage
  }
  final case object NoSuchOperation extends InvocationResult {
    override def toString = "no such operation"
  }
  final case class NoOperationWithSignature(signature: Seq[Class[_]], validSignatures: Seq[String]) extends InvocationResult {
    override def toString = "no operation with signature (%s) - valid signatures:%n  %s".format(
      signature.map { _.getSimpleName },
      validSignatures.mkString("%n  ".format())
    )
  }
  final case class AmbiguousSignature(signatures: Seq[String]) extends InvocationResult {
    override def toString = "ambiguous signatures:%n  %s".format(
      signatures.mkString("%n  ".format())
    )
  }
}
