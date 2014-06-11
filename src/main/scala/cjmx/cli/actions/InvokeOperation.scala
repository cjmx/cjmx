package cjmx.cli
package actions

import scala.collection.JavaConverters._
import scalaz._
import Scalaz._

import javax.management.{ JMX => _, _ }

import cjmx.util.jmx._
import JMX._


case class InvokeOperation(query: MBeanQuery, operationName: String, params: Seq[AnyRef]) extends ConnectedAction {
  def applyConnected(context: ActionContext, connection: JMXConnection) = {
    val svr = connection.mbeanServer
    val names = svr.toScala.queryNames(query).toSeq.sorted
    val results = names map { name =>
      val info = svr.getMBeanInfo(name)
      val operationsWithSameName = info.getOperations.toList.filter { _.getName == operationName }
      if (operationsWithSameName.isEmpty) {
        name -> InvocationResults.NoSuchOperation
      } else {
        val operationsWithMatchingSignature = operationsWithSameName filter matchesSignature
        name -> (operationsWithMatchingSignature match {
          case Nil =>
            InvocationResults.NoOperationWithSignature(
              signature = params.map { _.getClass },
              validSignatures = operationsWithSameName.map(showSignatures))
          case op :: Nil =>
            try InvocationResults.Succeeded(svr.invoke(name, operationName, params.toArray, op.getSignature.map { _.getType }))
            catch {
              case e: Exception =>
                InvocationResults.Failed(e)
            }
          case other =>
            InvocationResults.AmbiguousSignature(other.map(showSignatures))
        })
      }
    }
    val msgs = context.formatter.formatInvocationResults(results)
    val sc = results.foldLeft(0) { case (acc, (name, res)) => acc max toStatusCode(res) }
    (context.withStatusCode(sc), enumMessageSeq(msgs))
  }

  private def matchesSignature(op: MBeanOperationInfo): Boolean =  {
    val sig = op.getSignature
    sig.size == params.size &&
      signatureTypes(op).cata(ts => (ts zip params).foldLeft(true) {
        case (acc, (s, p)) => acc && s.isAssignableFrom(p.getClass)
      }, false)
  }

  private def signatureTypes(op: MBeanOperationInfo): Option[List[Class[_]]] =
    op.getSignature.toList.map { pi => typeToClass(getClass.getClassLoader)(pi.getType) }.sequenceU

  private def showSignatures(op: MBeanOperationInfo): String =
    op.getSignature.map { t => JMXTags.Type(t.getType).shows }.mkString("(", ", ", ")")

  private def toStatusCode(res: InvocationResult) = res match {
    case _: InvocationResults.Succeeded => 0
    case InvocationResults.NoSuchOperation => 1
    case _: InvocationResults.AmbiguousSignature => 2
    case _: InvocationResults.NoOperationWithSignature => 3
    case _: InvocationResults.Failed => 4
  }
}

