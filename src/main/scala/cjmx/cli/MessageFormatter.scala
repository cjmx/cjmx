package cjmx.cli

import scala.collection.immutable.Seq

import javax.management.{ObjectName, Attribute, MBeanInfo}

trait MessageFormatter {
  def formatNames(names: Seq[ObjectName]): Seq[String]
  def formatAttributes(attrs: Seq[(ObjectName, Seq[Attribute])]): Seq[String]
  def formatInfo(info: Seq[(ObjectName, MBeanInfo)], detailed: Boolean): Seq[String]
  def formatInvocationResults(namesAndResults: Seq[(ObjectName, InvocationResult)]): Seq[String]
}

