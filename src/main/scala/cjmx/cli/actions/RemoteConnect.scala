package cjmx.cli
package actions

import com.sun.tools.attach._
import javax.management.remote._

import scalaz.{ @@, \/, State, Reader }
import scalaz.std.option._
import scalaz.stream.Process
import scalaz.syntax.either._

import cjmx.util.jmx.{Attach, JMXCredentials}
import cjmx.util.jmx.JMX._


case class RemoteConnect(host: String, port: Int, username: Option[String], quiet: Boolean) extends Action {
  private val uri = s"service:jmx:rmi:///jndi/rmi://$host:$port/jmxrmi"
  
  def apply(context: ActionContext) = {
    def buildCredentials(username: Option[String]): String \/ Option[JMXCredentials] = 
      username match {
        case Some(user) => 
          val password: Option[String] = context.readLine("Password: ", Some('*')).filter { _.nonEmpty }
          password match {
            case Some(p) => some(JMXCredentials(user, p)).right
            case None => "No password specified.".left
          }
        case None => none.right
      }

    val result = for {
      credentials <- buildCredentials(username)
      connection <- Attach.remoteConnect(uri, credentials) 
    } yield connection 

    result fold(
      err => (context.withStatusCode(1), Process.emit(err)),
      cnx => {
        val server = cnx.getMBeanServerConnection
        (context.connected(cnx), enumMessageList(if (quiet) List.empty else List(
          "Connected to remote virtual machine %s".format(uri),
          "Connection id: %s".format(cnx.getConnectionId),
          "Default domain: %s".format(server.getDefaultDomain),
          "%d domains registered consisting of %d total MBeans".format(server.getDomains.length, server.getMBeanCount)
        )))
      }
    )
  }

}
