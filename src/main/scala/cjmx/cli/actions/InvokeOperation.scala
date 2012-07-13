package cjmx.cli
package actions

import scala.collection.JavaConverters._
import scalaz._
import Scalaz._

import javax.management._
import javax.management.remote.JMXConnector

import cjmx.util.jmx._


case class InvokeOperation(query: MBeanQuery, operationName: String, params: Seq[AnyRef]) extends SimpleConnectedAction {
  def act(context: ActionContext, connection: JMXConnector) = {
    val svr = connection.getMBeanServerConnection
    val names = svr.toScala.queryNames(query).toSeq.sorted
    val results = names map { name =>
      val info = svr.getMBeanInfo(name)
      val operationsWithSameName = info.getOperations.toList.filter { _.getName == operationName }
      if (operationsWithSameName.isEmpty) {
        name -> Left("no such operation")
      } else {
        val operationsWithMatchingSignature = operationsWithSameName filter matchesSignature
        name -> (operationsWithMatchingSignature match {
          case Nil =>
            Left("no operation with signature (%s) - valid signatures:%n  %s".format(
              params.map { _.getClass.getSimpleName }.mkString(", "),
              operationsWithSameName.map(showSignatures).mkString("%n  ".format())))
          case op :: Nil =>
            Right(JMXTags.Value(svr.invoke(name, operationName, params.toArray, op.getSignature.map { _.getType })).shows)
          case other =>
            Left("ambiguous signature")
        })
      }
    }
    context.formatter.formatInvocationResults(results).success
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

