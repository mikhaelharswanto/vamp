package io.vamp.operation.deployment

import akka.actor.ActorLogging
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ CommonSupportForActors, IoC }
import io.vamp.common.config.Config
import io.vamp.container_driver.{ ContainerDriverActor, ContainerInstance, ContainerService, Containers }
import io.vamp.model.artifact.DeploymentService.State.Intention
import io.vamp.model.artifact.DeploymentService.State.Step.{ Done, Update }
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.model.resolver.DeploymentTraitResolver
import io.vamp.operation.gateway.GatewayActor
import io.vamp.operation.notification.OperationNotificationProvider
import io.vamp.persistence.db.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.persistence.operation.{ DeploymentPersistence, DeploymentServiceEnvironmentVariables, DeploymentServiceInstances, DeploymentServiceState }
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.{ PulseActor, PulseEventTags }

object SingleDeploymentSynchronizationActor {

  case class Synchronize(containerService: ContainerService)

}

class SingleDeploymentSynchronizationActor extends DeploymentGatewayOperation with ArtifactPaginationSupport with CommonSupportForActors with DeploymentTraitResolver with OperationNotificationProvider with ActorLogging {

  import DeploymentPersistence._
  import PulseEventTags.Deployments._
  import SingleDeploymentSynchronizationActor._

  private val config = Config.config("vamp.operation")

  private val checkCpu = config.boolean("synchronization.check.cpu")

  private val checkMemory = config.boolean("synchronization.check.memory")

  private val checkInstances = config.boolean("synchronization.check.instances")

  def receive: Receive = {
    case Synchronize(containerService) ⇒ synchronize(containerService)
    case _                             ⇒
  }

  private def synchronize(containerService: ContainerService) = {

    log.info(s"Synchronizing deployment ${containerService.service.breed.name}...")

    containerService.deployment.clusters.find { cluster ⇒ cluster.services.exists(_.breed.name == containerService.service.breed.name) } match {
      case Some(cluster) ⇒

        val service = containerService.service
        val deployment = containerService.deployment

        service.state.intention match {
          case Intention.Deploy if service.state.isDone ⇒ redeployIfNeeded(deployment, cluster, service, containerService.containers)
          case Intention.Deploy                         ⇒ deploy(deployment, cluster, service, containerService.containers)
          case Intention.Undeploy                       ⇒ undeploy(deployment, cluster, service, containerService.containers)
          case _                                        ⇒
        }

      case _ ⇒
    }
  }

  private def deploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containers: Option[Containers]) = {

    def deployTo(update: Boolean, ds: DeploymentService = deploymentService) = actorFor[ContainerDriverActor] ! ContainerDriverActor.Deploy(deployment, deploymentCluster, ds, update = update)

    def convert(server: ContainerInstance): DeploymentInstance = {
      val ports = deploymentService.breed.ports.map(_.name) zip server.ports
      DeploymentInstance(server.name, server.host, ports.toMap, server.deployed)
    }

    containers match {
      case None ⇒
        if (hasDependenciesDeployed(deployment, deploymentCluster, deploymentService)) {
          if (hasResolvedEnvironmentVariables(deployment, deploymentCluster, deploymentService))
            deployTo(update = false)
          else
            resolveEnvironmentVariables(deployment, deploymentCluster, deploymentService)
        }

      case Some(cs) ⇒
        if (!matchingScale(deploymentService, cs) || !matchingEnvironmentVariables(deploymentService, cs)) {
          log.info(s"Deploying ${deployment.name}, contacting driver to update deployment...")
          deployTo(update = true, deploymentService.copy(environmentVariables = resolveEnvironmentVariables(deployment, deploymentCluster, deploymentService)))
          persist(DeploymentServiceInstances(serviceArtifactName(deployment, deploymentCluster, deploymentService), cs.instances.map(convert)))
          updateGateways(deployment, deploymentCluster)
        } else if (!matchingServers(deploymentService, cs)) {
          log.info(s"Deploying ${deployment.name}, updating service instances...")
          persist(DeploymentServiceInstances(serviceArtifactName(deployment, deploymentCluster, deploymentService), cs.instances.map(convert)))
          updateGateways(deployment, deploymentCluster)
        } else {
          log.info(s"Deploying ${deployment.name}, done...")
          persist(DeploymentServiceState(serviceArtifactName(deployment, deploymentCluster, deploymentService), deploymentService.state.copy(step = Done())))
          updateGateways(deployment, deploymentCluster)
          publishDeployed(deployment, deploymentCluster, deploymentService)
        }
    }
  }

  private def hasDependenciesDeployed(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService) = {
    deploymentService.breed.dependencies.forall {
      case (n, d) ⇒
        deployment.clusters.exists { cluster ⇒
          cluster.services.find(s ⇒ matchDependency(d)(s.breed)) match {
            case None ⇒ false
            case Some(service) ⇒ service.state.isDeployed && service.breed.ports.forall {
              port ⇒ cluster.serviceBy(port.name).isDefined
            }
          }
        }
    }
  }

  private def hasResolvedEnvironmentVariables(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    service.breed.environmentVariables.count(_ ⇒ true) <= service.environmentVariables.count(_ ⇒ true) && service.environmentVariables.forall(_.interpolated.isDefined)
  }

  private def resolveEnvironmentVariables(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): List[EnvironmentVariable] = {
    val clusterEnvironmentVariables = resolveEnvironmentVariables(deployment, cluster :: Nil)

    val local = (service.breed.environmentVariables.filter(_.value.isDefined) ++
      clusterEnvironmentVariables.flatMap(ev ⇒ TraitReference.referenceFor(ev.name) match {
        case Some(TraitReference(c, g, n)) ⇒
          if (g == TraitReference.groupFor(TraitReference.EnvironmentVariables) && ev.interpolated.isDefined && cluster.name == c && service.breed.environmentVariables.exists(_.name == n))
            ev.copy(name = n, alias = None) :: Nil
          else
            Nil
        case _ ⇒ Nil
      }) ++ service.environmentVariables).map(ev ⇒ ev.name -> ev).toMap.values.toList

    val environmentVariables = local.map { ev ⇒
      ev.copy(interpolated = if (ev.interpolated.isEmpty) Some(resolve(ev.value.getOrElse(""), valueForWithDependencyReplacement(deployment, service))) else ev.interpolated)
    }

    persist(DeploymentServiceEnvironmentVariables(serviceArtifactName(deployment, cluster, service), environmentVariables))
    environmentVariables
  }

  private def redeployIfNeeded(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containers: Option[Containers]) = {

    def redeploy() = {
      log.info(s"Redeploying ${deployment.name}...")
      persist(DeploymentServiceState(serviceArtifactName(deployment, deploymentCluster, deploymentService), deploymentService.state.copy(step = Update())))
      publishRedeploy(deployment, deploymentCluster, deploymentService)
      deploy(deployment, deploymentCluster, deploymentService, containers)
    }

    containers match {
      case None     ⇒ redeploy()
      case Some(cs) ⇒ if (!matchingServers(deploymentService, cs) || !matchingScale(deploymentService, cs) || !matchingEnvironmentVariables(deploymentService, cs)) redeploy()
      case _        ⇒ updateGateways(deployment, deploymentCluster)
    }
  }

  private def undeploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containers: Option[Containers]) = {
    containers match {
      case Some(_) ⇒
        actorFor[ContainerDriverActor] ! ContainerDriverActor.Undeploy(deployment, deploymentCluster, deploymentService)
        persist(DeploymentServiceState(serviceArtifactName(deployment, deploymentCluster, deploymentService), deploymentService.state.copy(step = Update())))
      case None ⇒
        persist(DeploymentServiceState(serviceArtifactName(deployment, deploymentCluster, deploymentService), deploymentService.state.copy(step = Done())))
        resetInnerRouteArtifacts(deployment, deploymentCluster, deploymentService)
        publishUndeployed(deployment, deploymentCluster, deploymentService)
    }
  }

  private def matchingServers(deploymentService: DeploymentService, containers: Containers) = {
    deploymentService.instances.size == containers.instances.size &&
      deploymentService.instances.forall { server ⇒
        server.deployed && (containers.instances.find(_.name == server.name) match {
          case None ⇒ false
          case Some(containerServer) ⇒ deploymentService.breed.ports.isEmpty ||
            (server.ports.size == containerServer.ports.size && server.ports.values.forall(port ⇒ containerServer.ports.contains(port)))
        })
      }
  }

  private def matchingScale(deploymentService: DeploymentService, containers: Containers) = {
    val cpu = if (checkCpu) containers.scale.cpu == deploymentService.scale.get.cpu else true
    val memory = if (checkMemory) containers.scale.memory == deploymentService.scale.get.memory else true
    val instances = if (checkInstances) containers.instances.size == deploymentService.scale.get.instances else true

    instances && cpu && memory
  }

  private def matchingEnvironmentVariables(deploymentService: DeploymentService, containers: Containers) = {
    deploymentService.environmentVariables == Nil ||
      containers.env == Nil ||
      deploymentService.environmentVariables
      .map(ev ⇒ (ev.name, ev.interpolated))
      .equals(containers.env)
  }

  private def updateGateways(deployment: Deployment, cluster: DeploymentCluster) = cluster.gateways.foreach { gateway ⇒
    IoC.actorFor[GatewayActor] ! GatewayActor.PromoteInner(gateway)
  }

  private def publishDeployed(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    actorFor[PulseActor] ! Publish(Event(tags(deployedTag, deployment, cluster, service), 0f, (deployment, cluster, service)), publishEventValue = false)
  }

  private def publishRedeploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    actorFor[PulseActor] ! Publish(Event(tags(redeployTag, deployment, cluster, service), 0f, (deployment, cluster, service)), publishEventValue = false)
  }

  private def publishUndeployed(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    actorFor[PulseActor] ! Publish(Event(tags(undeployedTag, deployment, cluster, service), 0f, (deployment, cluster)), publishEventValue = false)
  }

  private def tags(tag: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    Set(s"deployments${Event.tagDelimiter}${deployment.name}", s"clusters${Event.tagDelimiter}${cluster.name}", s"services${Event.tagDelimiter}${service.breed.name}", tag)
  }

  private def persist(artifact: Artifact) = actorFor[PersistenceActor] ! PersistenceActor.Update(artifact)
}
