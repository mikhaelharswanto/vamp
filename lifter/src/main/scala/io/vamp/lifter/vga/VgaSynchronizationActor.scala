package io.vamp.lifter.vga

import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.config.Config
import io.vamp.container_driver.{ ContainerDriverActor, DockerPortMapping }
import io.vamp.lifter.notification.LifterNotificationProvider

trait VgaSynchronizationActor extends CommonSupportForActors with LifterNotificationProvider {

  protected implicit val timeout = ContainerDriverActor.timeout

  private val scale = Config.config("vamp.lifter.vamp-gateway-agent.scale")
  protected val cpu = scale.double("cpu")
  protected val mem = scale.int("memory")

  protected val config = Config.config("vamp.lifter.vamp-gateway-agent.synchronization")

  protected val id = config.string("id")
  protected val image = config.string("container.image")
  protected val network = config.string("container.network")
  protected val command = config.stringList("container.command")
  protected val ports = config.intList("container.ports").map(port ⇒ DockerPortMapping(port, "tcp", port))
}
