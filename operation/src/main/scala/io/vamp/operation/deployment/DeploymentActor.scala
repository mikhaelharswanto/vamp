package io.vamp.operation.deployment

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.config.Config
import io.vamp.common.notification.{ NotificationErrorException, NotificationProvider }
import io.vamp.model.artifact.DeploymentService.State.Intention._
import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader._
import io.vamp.model.resolver.DeploymentTraitResolver
import io.vamp.operation.deployment.DeploymentSynchronizationActor.Synchronize
import io.vamp.operation.gateway.GatewayActor
import io.vamp.operation.notification._
import io.vamp.persistence.db._
import io.vamp.persistence.operation.DeploymentPersistence._
import io.vamp.persistence.operation._

import scala.concurrent.Future
import scala.language.{ existentials, postfixOps }
import scala.util.Try

object DeploymentActor {

  private val config = Config

  val gatewayHost = config.string("vamp.gateway-driver.host")

  val defaultScale = config.config("vamp.operation.deployment.scale") match {
    case c ⇒ DefaultScale("", Quantity.of(c.double("cpu")), MegaByte.of(c.string("memory")), c.int("instances"))
  }

  val defaultArguments: List[Argument] = {
    val arguments = config.stringList("vamp.operation.deployment.arguments").map {
      _.split("=", 2).toList match {
        case key :: value :: Nil ⇒ Argument(key.trim, value.trim)
        case any                 ⇒ throw NotificationErrorException(InvalidArgumentError, if (any != null) any.toString else "")
      }
    }

    arguments.foreach { argument ⇒
      if (argument.privileged && Try(argument.value.toBoolean).isFailure) throw NotificationErrorException(InvalidArgumentValueError(argument), s"${argument.key} -> ${argument.value}")
    }

    arguments
  }

  trait DeploymentMessage

  case class Create(blueprint: Blueprint, source: String, validateOnly: Boolean) extends DeploymentMessage

  case class Merge(name: String, blueprint: Blueprint, source: String, validateOnly: Boolean) extends DeploymentMessage

  case class Slice(name: String, blueprint: Blueprint, source: String, validateOnly: Boolean) extends DeploymentMessage

  case class UpdateSla(deployment: Deployment, cluster: DeploymentCluster, sla: Option[Sla], source: String) extends DeploymentMessage

  case class UpdateScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale, source: String) extends DeploymentMessage

  case class UpdateGateways(deployment: Deployment, cluster: DeploymentCluster, gateways: List[Gateway], source: String) extends DeploymentMessage

}

class DeploymentActor extends CommonSupportForActors with BlueprintSupport with DeploymentValidator with DeploymentMerger with DeploymentSlicer with DeploymentUpdate with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import DeploymentActor._

  def receive = {
    case Create(blueprint, source, validateOnly) ⇒ reply {
      (merge(deploymentFor(blueprint), validateOnly) andThen commit(source, validateOnly))(deploymentFor(blueprint.name, create = true))
    }

    case Merge(name, blueprint, source, validateOnly) ⇒ reply {
      (merge(deploymentFor(blueprint), validateOnly) andThen commit(source, validateOnly))(deploymentFor(name, create = true))
    }

    case Slice(name, blueprint, source, validateOnly) ⇒ reply {
      (slice(deploymentFor(blueprint), validateOnly) andThen commit(source, validateOnly))(deploymentFor(name))
    }

    case UpdateSla(deployment, cluster, sla, source) ⇒ reply {
      updateSla(deployment, cluster, sla, source)
    }

    case UpdateScale(deployment, cluster, service, scale, source) ⇒ reply {
      updateScale(deployment, cluster, service, scale, source)
    }

    case any ⇒ unsupported(UnsupportedDeploymentRequest(any))
  }

  def commit(source: String, validateOnly: Boolean): (Future[Deployment] ⇒ Future[Any]) = { future ⇒
    if (validateOnly) future
    else future.flatMap {
      case deployment ⇒
        implicit val timeout: Timeout = PersistenceActor.timeout
        checked[List[_]](IoC.actorFor[PersistenceActor] ? PersistenceActor.Update(deployment, Some(source))) map {
          case persisted ⇒
            IoC.actorFor[DeploymentSynchronizationActor] ! Synchronize(deployment)
            persisted
        }
    }
  }
}

trait BlueprintSupport extends DeploymentValidator with NameValidator with BlueprintGatewayHelper with ArtifactExpansionSupport {
  this: DeploymentTraitResolver with ActorSystemProvider with ArtifactPaginationSupport with ExecutionContextProvider with NotificationProvider ⇒

  def deploymentFor(name: String, create: Boolean = false): Future[Deployment] = {
    if (!create) {
      artifactFor[Deployment](name)
    } else {
      artifactForIfExists[Deployment](name) map {
        case Some(deployment) ⇒ deployment
        case None ⇒
          validateName(name)
          Deployment(name, clusters = Nil, gateways = Nil, ports = Nil, environmentVariables = Nil, hosts = Nil)
      }
    }
  }

  def deploymentFor(blueprint: Blueprint): Future[Deployment] = {
    artifactFor[DefaultBlueprint](blueprint) flatMap {
      case bp ⇒

        val clusters = bp.clusters.map { cluster ⇒
          for {
            services ← Future.traverse(cluster.services)({ service ⇒
              for {
                breed ← artifactFor[DefaultBreed](service.breed)
                scale ← artifactFor[DefaultScale](service.scale)
              } yield {
                DeploymentService(Deploy, breed, service.environmentVariables, scale, Nil, arguments(breed, service), Map(), service.dialects)
              }
            })
            gateways ← expandGateways(cluster.gateways)

          } yield {
            DeploymentCluster(cluster.name, services, processAnonymousInnerGateways(services, gateways), cluster.sla, cluster.dialects)
          }
        }

        for {
          c ← Future.sequence(clusters)
          g ← expandGateways(bp.gateways)
        } yield {
          Deployment(blueprint.name, c, g, Nil, bp.environmentVariables, Nil)
        }
    }
  }

  private def arguments(breed: DefaultBreed, service: Service): List[Argument] = {
    val all = DeploymentActor.defaultArguments ++ breed.arguments ++ service.arguments

    val (privileged, others) = all.partition(_.privileged)

    privileged.lastOption.map(_ :: others).getOrElse(others)
  }
}

trait DeploymentValidator {
  this: BlueprintGatewayHelper with DeploymentTraitResolver with ArtifactPaginationSupport with ArtifactSupport with ExecutionContextProvider with NotificationProvider ⇒

  def validateServices: (Deployment ⇒ Deployment) = { (deployment: Deployment) ⇒
    val services = deployment.clusters.flatMap(_.services).filterNot(_.state.intention == Undeploy)

    val breeds = services.map(_.breed)

    breeds.groupBy(_.name.toString).collect {
      case (name, list) if list.size > 1 ⇒ throwException(NonUniqueBreedReferenceError(list.head))
    }

    breeds.foreach { breed ⇒
      breed.dependencies.values.find { dependency ⇒
        !breeds.filterNot(_.name == breed.name).exists(matchDependency(dependency))
      } flatMap { dependency ⇒
        throwException(UnresolvedDependencyError(breed, dependency))
      }
    }

    breeds.foreach(BreedReader.validateNonRecursiveDependencies)

    services.foreach { service ⇒
      service.environmentVariables.foreach { environmentVariable ⇒
        if (!service.breed.environmentVariables.exists(_.name == environmentVariable.name)) throwException(UnresolvedDependencyInTraitValueError(service.breed, environmentVariable.name))
      }
    }

    deployment
  }

  def validateInnerGateways: (Deployment ⇒ Deployment) = validateRouteWeights andThen validateBlueprintGateways andThen validateInnerGatewayAnonymousPortMapping

  def validateRouteWeights: (Deployment ⇒ Deployment) = { (deployment: Deployment) ⇒
    deployment.clusters.map(cluster ⇒

      cluster -> weightOf(cluster, cluster.services, "")).find({
      case (cluster, weight) ⇒ weight != 100 && weight != 0
    }

    ).flatMap({
      case (cluster, weight) ⇒ throwException(UnsupportedRouteWeight(deployment, cluster, weight))
    })

    deployment
  }

  def validateScaleEscalations: (Deployment ⇒ Deployment) = { (deployment: Deployment) ⇒
    BlueprintReader.validateScaleEscalations(deployment)
    deployment
  }

  def validateBlueprintEnvironmentVariables: (Future[Deployment] ⇒ Future[Deployment]) = { (futureBlueprint: Future[Deployment]) ⇒
    futureBlueprint.map {
      case bp: AbstractBlueprint ⇒
        bp.environmentVariables.find(ev ⇒ !traitExists(bp, TraitReference.referenceFor(ev.name), strictBreeds = true)).flatMap {
          case t ⇒ throwException(UnresolvedEnvironmentVariableError(t.name, t.value.getOrElse("")))
        }.getOrElse(bp)
      case blueprint ⇒ blueprint
    }
  }

  def validateBlueprintRoutes: (Future[Deployment] ⇒ Future[Deployment]) = { (futureBlueprint: Future[Deployment]) ⇒
    // Reference check.
    futureBlueprint.map {
      case bp: AbstractBlueprint ⇒
        val ports = bp.gateways.flatMap { gateway ⇒
          gateway.routes.map { route ⇒
            if (route.length != 2) throwException(UnresolvedGatewayPortError(route.path.source, gateway.port.value.get))
            gateway.port.copy(name = TraitReference(route.path.segments.head, TraitReference.Ports, route.path.segments.tail.head).reference)
          }
        }

        ports.find(ev ⇒ !traitExists(bp, TraitReference.referenceFor(ev.name), strictBreeds = true)).flatMap {
          case t ⇒ throwException(UnresolvedGatewayPortError(t.name, t.value))
        }.getOrElse(bp)
      case blueprint ⇒ blueprint
    }
    futureBlueprint
  }

  def validateGateways: (Deployment ⇒ Future[Deployment]) = { (deployment: Deployment) ⇒
    // Availability check.
    implicit val timeout = PersistenceActor.timeout
    allArtifacts[Gateway] map {
      case gateways ⇒
        val otherGateways = gateways.filter(gateway ⇒ GatewayPath(gateway.name).segments.head != deployment.name)

        deployment.gateways.map { gateway ⇒
          otherGateways.find(_.port.number == gateway.port.number) match {
            case Some(g) ⇒ throwException(UnavailableGatewayPortError(gateway.port, g))
            case _       ⇒ gateway
          }
        }

        deployment
    }
  }

  def traitExists(blueprint: AbstractBlueprint, reference: Option[TraitReference], strictBreeds: Boolean): Boolean = reference match {
    case Some(TraitReference(cluster, group, local)) ⇒
      blueprint.clusters.find(_.name == cluster) match {
        case None ⇒ false
        case Some(c) ⇒ c.services.exists({
          service ⇒
            service.breed match {
              case breed: DefaultBreed ⇒ breed.traitsFor(group).exists(_.name.toString == local)
              case _                   ⇒ !strictBreeds
            }
        })
      }

    case _ ⇒ false
  }

  def weightOf(cluster: DeploymentCluster, services: List[DeploymentService], port: String): Int = cluster.gatewayBy(port).flatMap({ routing ⇒
    Some(routing.routes.filter({
      case route: DefaultRoute ⇒ route.path.segments match {
        case _ :: _ :: s :: _ :: Nil ⇒ services.exists { service ⇒ service.breed.name == s }
        case _                       ⇒ true
      }
      case _ ⇒ true
    }).map({
      case route: DefaultRoute ⇒ route.weight.getOrElse(Percentage(0)).value
      case route               ⇒ throwException(InternalServerError(s"unsupported route: $route"))
    }).sum)
  }).getOrElse(0)
}

trait DeploymentOperation extends DeploymentGatewayOperation {
  this: ActorSystemProvider with NotificationProvider ⇒

  def commit(source: String, validateOnly: Boolean): (Future[Deployment] ⇒ Future[Any])
}

trait DeploymentGatewayOperation {
  this: ActorSystemProvider with NotificationProvider ⇒

  def serviceRoutePath(deployment: Deployment, cluster: DeploymentCluster, serviceName: String, portName: String) = GatewayPath(deployment.name :: cluster.name :: serviceName :: portName :: Nil)

  def updateRoutePaths(deployment: Deployment, cluster: DeploymentCluster, gateway: Gateway) = {
    gateway.copy(
      name = GatewayPath(deployment.name :: cluster.name :: gateway.port.name :: Nil).normalized,
      routes = gateway.routes.map {
        case route: DefaultRoute if route.length == 1 ⇒ route.copy(path = serviceRoutePath(deployment, cluster, route.path.normalized, gateway.port.name))
        case route if route.length == 4               ⇒ route
        case route                                    ⇒ throwException(InternalServerError(s"unsupported cluster route: ${route.length}"))
      })
  }

  def resetServiceArtifacts(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, state: DeploymentService.State = Deploy) = {
    actorFor[PersistenceActor] ! PersistenceActor.Update(DeploymentServiceState(serviceArtifactName(deployment, cluster, service), state))

    val name = serviceArtifactName(deployment, cluster, service)

    actorFor[PersistenceActor] ! PersistenceActor.Delete(name, classOf[DeploymentServiceInstances])
    actorFor[PersistenceActor] ! PersistenceActor.Delete(name, classOf[DeploymentServiceEnvironmentVariables])

    actorFor[PersistenceActor] ! PersistenceActor.Delete(name, classOf[GatewayPort])
    actorFor[PersistenceActor] ! PersistenceActor.Delete(name, classOf[GatewayServiceAddress])
    actorFor[PersistenceActor] ! PersistenceActor.Delete(name, classOf[GatewayDeploymentStatus])
    actorFor[PersistenceActor] ! PersistenceActor.Delete(name, classOf[RouteTargets])
    actorFor[PersistenceActor] ! PersistenceActor.Delete(name, classOf[InnerGateway])
  }

  def resetInnerRouteArtifacts(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    service.breed.ports.foreach { port ⇒
      actorFor[PersistenceActor] ! PersistenceActor.Delete(servicePortArtifactName(deployment, cluster, service, port), classOf[RouteTargets])
    }
  }
}

trait DeploymentMerger extends DeploymentOperation with DeploymentTraitResolver {
  this: ReplyActor with DeploymentValidator with ArtifactSupport with ActorSystemProvider with ExecutionContextProvider with NotificationProvider ⇒

  def validateBlueprint = validateBlueprintEnvironmentVariables andThen validateBlueprintRoutes

  def resolveProperties = resolveHosts andThen validateEmptyVariables andThen resolveDependencyMapping

  def validateMerge = validateServices andThen validateInnerGateways andThen validateScaleEscalations andThen validateGateways

  def merge(blueprint: Future[Deployment], validateOnly: Boolean): (Future[Deployment] ⇒ Future[Deployment]) = { (futureDeployment: Future[Deployment]) ⇒
    futureDeployment.flatMap {
      case deployment ⇒
        (validateBlueprint andThen resolveProperties)(blueprint) flatMap {
          case attachment ⇒
            mergeClusters(futureDeployment, attachment, validateOnly) flatMap {
              case clusters ⇒
                val gateways = mergeGateways(attachment, deployment)
                val ports = mergeTrait(attachment.ports, deployment.ports)
                val environmentVariables = mergeTrait(attachment.environmentVariables, deployment.environmentVariables)
                val hosts = mergeTrait(attachment.hosts, deployment.hosts)

                validateMerge(Deployment(deployment.name, clusters, gateways, ports, environmentVariables, hosts)) flatMap {
                  deployment ⇒
                    implicit val timeout = GatewayActor.timeout
                    Future.sequence {
                      gateways.map { gateway ⇒
                        if (deployment.gateways.exists(_.name == gateway.name))
                          actorFor[GatewayActor] ? GatewayActor.Update(gateway, None, validateOnly = validateOnly, promote = true, force = true)
                        else
                          actorFor[GatewayActor] ? GatewayActor.Create(gateway, None, validateOnly = validateOnly, force = true)
                      }
                    } map (_ ⇒ deployment)
                }
            }
        }
    }
  }

  def mergeGateways(blueprint: Deployment, deployment: Deployment): List[Gateway] = {
    blueprint.gateways.map(processGateway(deployment)) ++ deployment.gateways.filterNot(gateway ⇒ blueprint.gateways.exists(_.port.number == gateway.port.number))
  }

  def mergeTrait[A <: Trait](traits1: List[A], traits2: List[A]): List[A] =
    (traits1.map(t ⇒ t.name -> t).toMap ++ traits2.map(t ⇒ t.name -> t).toMap).values.toList

  def mergeClusters(futureStable: Future[Deployment], blueprint: Deployment, validateOnly: Boolean): Future[List[DeploymentCluster]] = {
    futureStable.flatMap {
      case stable ⇒
        val deploymentClusters = stable.clusters.filter(cluster ⇒ !blueprint.clusters.exists(_.name == cluster.name)).map(Future(_))

        val blueprintClusters = blueprint.clusters.map { cluster ⇒
          stable.clusters.find(_.name == cluster.name) match {
            case None ⇒
              mergeServices(stable, None, cluster, validateOnly).flatMap {
                case services ⇒
                  val nc = cluster.copy(services = services)
                  updatedRouting(stable, None, nc, validateOnly) map {
                    routing ⇒ nc.copy(gateways = routing)
                  }
              }
            case Some(deploymentCluster) ⇒
              mergeServices(stable, Some(deploymentCluster), cluster, validateOnly).flatMap {
                case services ⇒
                  val nc = deploymentCluster.copy(
                    services = services,
                    dialects = deploymentCluster.dialects ++ cluster.dialects,
                    gateways = if (cluster.gateways.nonEmpty) cluster.gateways else deploymentCluster.gateways,
                    sla = if (cluster.sla.isDefined) cluster.sla else deploymentCluster.sla
                  )
                  updatedRouting(stable, Some(deploymentCluster), nc, validateOnly) map {
                    routing ⇒ nc.copy(gateways = routing)
                  }
              }
          }
        }

        Future.sequence(deploymentClusters ++ blueprintClusters)
    }
  }

  def mergeServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster, validateOnly: Boolean): Future[List[DeploymentService]] = {
    Future.sequence {
      mergeOldServices(deployment, stableCluster, blueprintCluster, validateOnly) ++ mergeNewServices(deployment, stableCluster, blueprintCluster, validateOnly)
    }
  }

  def mergeOldServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster, validateOnly: Boolean): List[Future[DeploymentService]] = stableCluster match {
    case None ⇒ Nil
    case Some(sc) ⇒ sc.services.map { service ⇒
      Future {
        blueprintCluster.services.find(_.breed.name == service.breed.name) match {
          case None ⇒ service
          case Some(bpService) ⇒

            val scale = if (bpService.scale.isDefined) bpService.scale else service.scale
            val state: DeploymentService.State = if (service.scale != bpService.scale || sc.gateways != blueprintCluster.gateways) Deploy else service.state

            if (!validateOnly) resetServiceArtifacts(deployment, blueprintCluster, service, state)

            service.copy(scale = scale, breed = bpService.breed, dialects = service.dialects ++ bpService.dialects, environmentVariables = bpService.environmentVariables)
        }
      }
    }
  }

  def mergeNewServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster, validateOnly: Boolean): List[Future[DeploymentService]] = {
    val (newServices, _) = newService(stableCluster, blueprintCluster)

    if (newServices.nonEmpty) {
      newServices.map {
        case service ⇒
          if (!validateOnly) resetServiceArtifacts(deployment, blueprintCluster, service)
          val scale = service.scale match {
            case None                      ⇒ DeploymentActor.defaultScale
            case Some(scale: DefaultScale) ⇒ scale
          }
          Future.successful(service.copy(scale = Some(scale)))
      }
    } else Nil
  }

  def newService(stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): (List[DeploymentService], List[DeploymentService]) =
    blueprintCluster.services.partition(service ⇒ isNewService(stableCluster, service))

  def isNewService(stableCluster: Option[DeploymentCluster], blueprintService: DeploymentService) = stableCluster match {
    case None     ⇒ true
    case Some(sc) ⇒ !sc.services.exists(_.breed.name == blueprintService.breed.name)
  }

  def updatedRouting(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster, validateOnly: Boolean): Future[List[Gateway]] = {

    def exists(gateway: Gateway) = stableCluster.exists(cluster ⇒ cluster.gateways.exists(_.port.name == gateway.port.name))

    implicit val timeout = PersistenceActor.timeout
    val routings = resolveRoutings(deployment, blueprintCluster)
    val promote = stableCluster.exists(cluster ⇒ cluster.gateways.size == blueprintCluster.gateways.size)

    Future.sequence {
      routings.map { gateway ⇒
        if (exists(gateway))
          checked[Gateway](IoC.actorFor[GatewayActor] ? GatewayActor.Update(updateRoutePaths(deployment, blueprintCluster, gateway), None, validateOnly = validateOnly, promote = promote, force = true))
        else
          checked[Gateway](IoC.actorFor[GatewayActor] ? GatewayActor.Create(updateRoutePaths(deployment, blueprintCluster, gateway), None, validateOnly = validateOnly, force = true))
      } ++ stableCluster.map(cluster ⇒ cluster.gateways.filterNot(routing ⇒ blueprintCluster.gateways.exists(_.port.name == routing.port.name))).getOrElse(Nil).map(Future.successful)
    }
  }

  private def resolveRoutings(deployment: Deployment, cluster: DeploymentCluster): List[Gateway] = {

    def routeBy(gateway: Gateway, service: DeploymentService, port: Port) = {
      gateway.routeBy(service.breed.name :: Nil).orElse(gateway.routeBy(deployment.name :: cluster.name :: service.breed.name :: port.name :: Nil))
    }

    val ports = cluster.services.flatMap(_.breed.ports).map(port ⇒ port.name -> port).toMap.values.toList

    ports.map { port ⇒
      val services = cluster.services.filter(_.breed.ports.exists(_.name == port.name))

      cluster.gatewayBy(port.name) match {

        case Some(newRouting) ⇒
          val routes = services.map { service ⇒
            routeBy(newRouting, service, port) match {
              case None        ⇒ DefaultRoute("", serviceRoutePath(deployment, cluster, service.breed.name, port.name), None, None, Nil, Nil, None)
              case Some(route) ⇒ route
            }
          }
          newRouting.copy(routes = routes, port = newRouting.port.copy(`type` = port.`type`))

        case None ⇒
          Gateway("", Port(port.name, None, None, 0, port.`type`), None, None, Nil, services.map { service ⇒
            DefaultRoute("", serviceRoutePath(deployment, cluster, service.breed.name, port.name), None, None, Nil, Nil, None)
          })
      }
    }
  }

  def processGateway(deployment: Deployment): Gateway ⇒ Gateway = { gateway ⇒
    val routes = gateway.routes.map {
      case route: DefaultRoute   ⇒ route.copy(path = GatewayPath(deployment.name :: route.path.segments))
      case route: RouteReference ⇒ route.copy(path = GatewayPath(deployment.name :: route.path.segments))
      case route                 ⇒ route
    }
    gateway.copy(name = Deployment.gatewayNameFor(deployment, gateway), routes = routes)
  }

  def resolveHosts: (Future[Deployment] ⇒ Future[Deployment]) = { (futureDeployment: Future[Deployment]) ⇒
    futureDeployment.map {
      case d ⇒ d.copy(hosts = d.clusters.map(cluster ⇒ Host(TraitReference(cluster.name, TraitReference.Hosts, Host.host).toString, Some(DeploymentActor.gatewayHost))))
    }
  }

  def validateEmptyVariables: (Future[Deployment] ⇒ Future[Deployment]) = { (futureDeployment: Future[Deployment]) ⇒
    futureDeployment.map {
      case deployment ⇒
        deployment.clusters.flatMap({ cluster ⇒
          cluster.services.flatMap(service ⇒ {
            service.breed.ports.filter(_.value.isEmpty).map(port ⇒ {
              val name = TraitReference(cluster.name, TraitReference.Ports, port.name).toString
              deployment.environmentVariables.find(_.name == name).getOrElse(throwException(UnresolvedVariableValueError(service.breed, port.name)))
            })

            service.breed.environmentVariables.filter(_.value.isEmpty).map(environmentVariable ⇒ {
              val name = TraitReference(cluster.name, TraitReference.EnvironmentVariables, environmentVariable.name).toString
              deployment.environmentVariables.find(_.name == name).getOrElse(throwException(UnresolvedVariableValueError(service.breed, environmentVariable.name)))
            })
          })
        })
        deployment
    }
  }

  def resolveDependencyMapping: (Future[Deployment] ⇒ Future[Deployment]) = { (futureDeployment: Future[Deployment]) ⇒
    futureDeployment.map {
      case deployment ⇒
        val dependencies = deployment.clusters.flatMap(cluster ⇒ cluster.services.map(service ⇒ (service.breed.name, cluster.name))).toMap
        deployment.copy(clusters = deployment.clusters.map({ cluster ⇒
          cluster.copy(services = cluster.services.map({ service ⇒
            service.copy(dependencies = service.breed.dependencies.flatMap({
              case (name, breed) ⇒
                dependencies.get(breed.name) match {
                  case Some(d) ⇒ (name, d) :: Nil
                  case None    ⇒ Nil
                }
            }))
          }))
        }))
    }
  }
}

trait DeploymentSlicer extends DeploymentOperation {
  this: DeploymentValidator with ArtifactSupport with ActorSystemProvider with ExecutionContextProvider with NotificationProvider ⇒

  def validateRoutingWeightOfServicesForRemoval(deployment: Deployment, blueprint: Deployment) = deployment.clusters.foreach { cluster ⇒
    blueprint.clusters.find(_.name == cluster.name).foreach { bpc ⇒
      val services = cluster.services.filterNot(service ⇒ bpc.services.exists(_.breed.name == service.breed.name))
      services.flatMap(_.breed.ports).map(_.name).toSet[String].foreach { port ⇒
        val weight = weightOf(cluster, services, port)
        if (weight != 100 && !(weight == 0 && services.isEmpty)) throwException(InvalidRouteWeight(deployment, cluster, weight))
      }
    }
  }

  def slice(futureBlueprint: Future[Deployment], validateOnly: Boolean): (Future[Deployment] ⇒ Future[Deployment]) = { (stableFuture: Future[Deployment]) ⇒
    futureBlueprint.flatMap { blueprint ⇒
      stableFuture.flatMap { stable ⇒

        validateRoutingWeightOfServicesForRemoval(stable, blueprint)

        val newClusters = stable.clusters.map(cluster ⇒
          blueprint.clusters.find(_.name == cluster.name).map { bpc ⇒

            val services = cluster.services.map { service ⇒
              if (bpc.services.exists(service.breed.name == _.breed.name)) {
                if (!validateOnly) resetServiceArtifacts(stable, bpc, service, Undeploy)
                service.copy(state = Undeploy)
              } else service
            }

            val routing = cluster.gateways.map { gateway ⇒
              updateRoutePaths(stable, cluster, gateway.copy(routes = gateway.routes.filterNot { route ⇒
                route.path.segments match {
                  case _ :: _ :: s :: _ :: Nil ⇒ bpc.services.exists(_.breed.name == s)
                  case _                       ⇒ false
                }
              }))
            } map (updateRoutePaths(stable, cluster, _))

            cluster.copy(services = services, gateways = routing)

          } getOrElse cluster
        )

        val deployment = (validateServices andThen validateInnerGateways andThen validateScaleEscalations)(stable.copy(clusters = newClusters.filter(_.services.nonEmpty)))

        val (deleteRouting, updateRouting) = newClusters.partition(cluster ⇒ cluster.services.isEmpty || cluster.services.forall(_.state.intention == Undeploy))

        implicit val timeout = GatewayActor.timeout
        Future.sequence {
          deleteRouting.flatMap { cluster ⇒
            stable.clusters.find(_.name == cluster.name) match {
              case Some(c) ⇒ c.services.flatMap(_.breed.ports).map(_.name).distinct.map { portName ⇒
                actorFor[GatewayActor] ? GatewayActor.Delete(GatewayPath(deployment.name :: cluster.name :: portName :: Nil).normalized, validateOnly = validateOnly, force = true)
              }
              case None ⇒ List.empty[Future[_]]
            }
          } ++ updateRouting.flatMap { cluster ⇒
            stable.clusters.find(_.name == cluster.name) match {
              case Some(_) ⇒ cluster.gateways.map(updateRoutePaths(stable, cluster, _)).map { routing ⇒ actorFor[GatewayActor] ? GatewayActor.Update(routing, None, validateOnly = validateOnly, promote = true, force = true) }
              case None    ⇒ List.empty[Future[_]]
            }
          }
        } map { _ ⇒ deployment }
      }
    }
  }
}

trait DeploymentUpdate {
  this: DeploymentValidator with ActorSystemProvider ⇒

  private implicit val timeout = PersistenceActor.timeout

  def updateSla(deployment: Deployment, cluster: DeploymentCluster, sla: Option[Sla], source: String) = {
    val clusters = deployment.clusters.map(c ⇒ if (cluster.name == c.name) c.copy(sla = sla) else c)
    actorFor[PersistenceActor] ? PersistenceActor.Update(deployment.copy(clusters = clusters), Some(source))
  }

  def updateScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale, source: String) = {
    lazy val services = cluster.services.map(s ⇒ if (s.breed.name == service.breed.name) service.copy(scale = Some(scale), state = Deploy) else s)
    val clusters = deployment.clusters.map(c ⇒ if (c.name == cluster.name) c.copy(services = services) else c)
    actorFor[PersistenceActor] ? PersistenceActor.Update(deployment.copy(clusters = clusters), Some(source))
  }
}
