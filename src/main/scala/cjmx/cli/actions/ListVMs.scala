package cjmx.cli
package actions

import cjmx.util.jmx.Attach

object ListVMs extends SimpleAction {
  def act(context: ActionContext) = {
    val vms = Attach.localVMs
    val longestId = vms.foldLeft(0) { (longest, vm) => longest max vm.id.length }
    val vmStrings = vms map { vm => "%%-%ds %%s".format(longestId).format(vm.id, vm.displayName) }
    vmStrings
  }
}

