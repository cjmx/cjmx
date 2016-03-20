package cjmx
package cli
package actions

import cjmx.util.jmx.{ Attach, JMXCredentials }

case class RemoteConnect(host: String, port: Int, username: Option[String], quiet: Boolean) extends Action {
  private val uri = s"service:jmx:rmi:///jndi/rmi://$host:$port/jmxrmi"

  def apply(context: ActionContext) = {
    def buildCredentials(username: Option[String]): Either[String, Option[JMXCredentials]] =
      username match {
        case Some(user) =>
          val password: Option[String] = context.readLine("Password: ", Some('*')).filter { _.nonEmpty }
          password match {
            case Some(p) => Right(Some(JMXCredentials(user, p)))
            case None => Left("No password specified.")
          }
        case None => Right(None)
      }

    val result = for {
      credentials <- buildCredentials(username)
      connection <- Attach.remoteConnect(uri, credentials)
    } yield connection

    result match {
      case Left(err) => ActionResult(context.withStatusCode(1), List(err))
      case Right(cnx) =>
        val server = cnx.getMBeanServerConnection
        ActionResult(context.connected(cnx), if (quiet) List.empty else List(
          s"Connected to remote virtual machine $uri",
          s"Connection id: ${cnx.getConnectionId}",
          s"Default domain: ${server.getDefaultDomain}",
          s"${server.getDomains.length} domains registered consisting of ${server.getMBeanCount} total MBeans")
        )
    }
  }

}
