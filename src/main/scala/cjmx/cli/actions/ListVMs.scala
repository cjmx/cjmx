package cjmx.cli
package actions

import scalaz.syntax.validation._

import cjmx.util.jmx.JMX


object ListVMs extends SimpleAction {
  def act(context: ActionContext) = {
    val vms = JMX.localVMs
    val longestId = vms.foldLeft(0) { (longest, vm) => longest max vm.id.length }
    val vmStrings = vms map { vm => "%%-%ds %%s".format(longestId).format(vm.id, vm.displayName) }
    vmStrings.success
  }
}

