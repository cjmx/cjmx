package cjmx.cli

case class ActionFailed(msg: String) extends Exception(msg)
