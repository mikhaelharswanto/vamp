package io.vamp.container_driver

import io.vamp.common.akka._
import io.vamp.common.config.Config
import io.vamp.common.notification.Notification
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, ContainerResponseError }
import io.vamp.model.artifact.{ Deployment, _ }
import io.vamp.persistence.db.PersistenceActor
import io.vamp.persistence.operation.GatewayServiceAddress
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

object ContainerDriverActor {

  lazy val timeout = Config.timeout("vamp.container-driver.response-timeout")

  trait ContainerDriveMessage

  case class DeploymentServices(deployment: Deployment, services: List[DeploymentService])

  case class Get(deploymentServices: List[DeploymentServices]) extends ContainerDriveMessage

  case class Scale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) extends ContainerDriveMessage

  case class Deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) extends ContainerDriveMessage

  case class Undeploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) extends ContainerDriveMessage

  case class DeployedGateways(gateway: List[Gateway]) extends ContainerDriveMessage

}

case class Containers(scale: DefaultScale, instances: List[ContainerInstance], env: List[(String, Option[String])] = Nil)

case class ContainerService(deployment: Deployment, service: DeploymentService, containers: Option[Containers])

case class ContainerInstance(name: String, host: String, ports: List[Int], deployed: Boolean)

case class ContainerInfo(`type`: String, container: Any)

trait ContainerDriverActor extends PulseFailureNotifier with CommonSupportForActors with ContainerDriverNotificationProvider {

  implicit val timeout = ContainerDriverActor.timeout

  val gatewayServiceIp = Config.string("vamp.gateway-driver.host")

  protected def deployedGateways(gateways: List[Gateway]): Future[Any] = {
    gateways.filter {
      gateway ⇒ gateway.service.isEmpty && gateway.port.assigned
    } foreach {
      gateway ⇒ setGatewayService(gateway, gatewayServiceIp, gateway.port.number)
    }
    Future.successful(true)
  }

  protected def setGatewayService(gateway: Gateway, host: String, port: Int) = {
    IoC.actorFor[PersistenceActor].forward(PersistenceActor.Create(GatewayServiceAddress(gateway.name, host, port)))
  }

  override def errorNotificationClass = classOf[ContainerResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}

