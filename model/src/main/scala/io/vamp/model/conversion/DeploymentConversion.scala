package io.vamp.model.conversion

import io.vamp.model.artifact._

import scala.language.implicitConversions

object DeploymentConversion {
  implicit def deploymentConversion(deployment: Deployment): DeploymentConversion = new DeploymentConversion(deployment)
}

class DeploymentConversion(val deployment: Deployment) {

  def asBlueprint: DefaultBlueprint = {
    val clusters = deployment.clusters.map(cluster ⇒ {
      Cluster(cluster.name, cluster.services.map(service ⇒ Service(service.breed, service.environmentVariables, service.scale, service.arguments, service.dialects)), cluster.gateways, cluster.sla, cluster.dialects)
    })

    val environmentVariables = deployment.environmentVariables.filter { ev ⇒
      TraitReference.referenceFor(ev.name) match {
        case Some(TraitReference(cluster, group, name)) ⇒
          deployment.clusters.find(_.name == cluster) match {
            case None ⇒ false
            case Some(c) ⇒ c.services.map(_.breed).exists { breed ⇒
              breed.traitsFor(group).exists(_.name == name)
            }
          }
        case _ ⇒ false
      }
    } map (_.copy(interpolated = None))

    DefaultBlueprint(deployment.name, clusters, Nil, environmentVariables)
  }
}
