package cjmx.cli
package actions

import javax.management.{ JMX => _, _ }

import cjmx.util.jmx._
import JMX._

case class InvokeOperation(query: MBeanQuery, operationName: String, params: Seq[AnyRef]) extends ConnectedAction {
  def applyConnected(context: ActionContext, connection: JMXConnection) = {
    val svr = connection.mbeanServer
    val names = svr.toScala.queryNames(query).toList.sorted
    val results = names map { name =>
      val info = svr.getMBeanInfo(name)
      val operationsWithSameName = info.getOperations.toList.filter { _.getName == operationName }
      if (operationsWithSameName.isEmpty) {
        name -> InvocationResult.NoSuchOperation
      } else {
        val operationsWithMatchingSignature = operationsWithSameName filter matchesSignature
        name -> (operationsWithMatchingSignature match {
          case Nil =>
            InvocationResult.NoOperationWithSignature(
              signature = params.map { _.getClass },
              validSignatures = operationsWithSameName.map(showSignatures))
          case op :: Nil =>
            try InvocationResult.Succeeded(JValue(svr.invoke(name, operationName, params.toArray, op.getSignature.map { _.getType })))
            catch {
              case e: Exception =>
                InvocationResult.Failed(e)
            }
          case other =>
            InvocationResult.AmbiguousSignature(other.map(showSignatures))
        })
      }
    }
    val msgs = context.formatter.formatInvocationResults(results)
    val sc = results.foldLeft(0) { case (acc, (name, res)) => acc max toStatusCode(res) }
    ActionResult(context.withStatusCode(sc), msgs)
  }

  private def matchesSignature(op: MBeanOperationInfo): Boolean =  {
    val sig = op.getSignature
    sig.size == params.size &&
      signatureTypes(op).fold(false)(ts => (ts zip params).foldLeft(true) {
        case (acc, (s, p)) => acc && s.isAssignableFrom(p.getClass)
      })
  }

  private def signatureTypes(op: MBeanOperationInfo): Option[List[Class[_]]] =
    op.getSignature.toList.foldLeft(Option(Vector.empty[Class[_]])) { (acc, pi) =>
      for {
        soFar <- acc
        t <- typeToClass(getClass.getClassLoader)(pi.getType)
      } yield soFar :+ t
    }.map { _.toList }

  private def showSignatures(op: MBeanOperationInfo): String =
    op.getSignature.map { t => JType(t.getType).toString }.mkString("(", ", ", ")")

  private def toStatusCode(res: InvocationResult) = res match {
    case _: InvocationResult.Succeeded => 0
    case InvocationResult.NoSuchOperation => 1
    case _: InvocationResult.AmbiguousSignature => 2
    case _: InvocationResult.NoOperationWithSignature => 3
    case _: InvocationResult.Failed => 4
  }
}

