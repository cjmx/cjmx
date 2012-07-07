package cjmx.cli
package actions

import scala.collection.JavaConverters._
import scalaz._
import Scalaz._

import javax.management._
import javax.management.remote.JMXConnector

import cjmx.util.jmx._


case class InvokeOperation(name: Option[ObjectName], query: Option[QueryExp], operationName: String, params: Seq[AnyRef]) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnector) = {
    val svr = connection.getMBeanServerConnection
    val names = svr.toScala.queryNames(name, query).toList.sorted
    val out = new OutputBuilder
    names foreach { name =>
      val info = svr.getMBeanInfo(name)
      val operationsWithSameName = info.getOperations.toList.filter { _.getName == operationName }
      if (operationsWithSameName.isEmpty) {
        out <+ "%s: %s".format(name, "no such operation")
      } else {
        val operationsWithMatchingSignature = operationsWithSameName filter matchesSignature
        out <+ "%s: %s".format(name, operationsWithMatchingSignature match {
          case Nil =>
            "no operation with signature (%s) - valid signatures:%n  %s".format(
              params.map { _.getClass.getSimpleName }.mkString(", "),
              operationsWithSameName.map(showSignatures).mkString("%n  ".format())
            )
          case op :: Nil =>
            try JMXTags.Value(svr.invoke(name, operationName, params.toArray, op.getSignature.map { _.getType })).shows
            catch {
              case e: Exception => e.getMessage
            }
          case other => "ambiguous signature"
        })
      }
    }
    out.lines.success
  }

  private def matchesSignature(op: MBeanOperationInfo): Boolean =  {
    val sig = op.getSignature
    sig.size == params.size &&
      signatureTypes(op).fold(ts => (ts zip params).foldLeft(true) {
        case (acc, (s, p)) => acc && s.isAssignableFrom(p.getClass)
      }, false)
  }

  private def signatureTypes(op: MBeanOperationInfo): Option[List[Class[_]]] =
    op.getSignature.toList.map { pi => typeToClass(getClass.getClassLoader)(pi.getType) }.sequenceU

  private def showSignatures(op: MBeanOperationInfo): String =
    op.getSignature.map { t => JMXTags.Type(t.getType).shows }.mkString("(", ", ", ")")
}

