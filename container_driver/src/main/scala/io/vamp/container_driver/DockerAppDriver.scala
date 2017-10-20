package io.vamp.container_driver

import io.vamp.container_driver.ContainerDriverActor.ContainerDriveMessage

object DockerAppDriver {

  case class ScaleDockerApp(app: DockerApp, force: Boolean = false) extends ContainerDriveMessage

  case class DeployDockerApp(app: DockerApp, update: Boolean, force: Boolean = false) extends ContainerDriveMessage

  case class RetrieveDockerApp(app: String) extends ContainerDriveMessage

  case class UndeployDockerApp(app: String) extends ContainerDriveMessage

}