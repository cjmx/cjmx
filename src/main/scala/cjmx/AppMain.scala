package cjmx

import xsbti._


class AppMain extends xsbti.AppMain {

  def run(config: AppConfiguration) = {
    val statusCode = App.run(config.arguments)
    new Exit {
      def code = statusCode
    }
  }

}

