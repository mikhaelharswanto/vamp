package io.vamp.rest_api

import akka.util.Timeout
import io.vamp.common.akka.{ CommonSupportForActors, _ }
import io.vamp.common.http.RestApiBase
import io.vamp.operation.controller.MetricsController
import spray.http.StatusCodes.{ NotFound, OK }

trait MetricsRoute extends MetricsController with ExecutionContextProvider {
  this: CommonSupportForActors with RestApiBase ⇒

  implicit def timeout: Timeout

  val metricsRoutes = pathPrefix("metrics") {
    get {
      path("gateways" / Segment / Segment) { (gateway, metrics) ⇒
        pathEndOrSingleSlash {
          onSuccess(gatewayMetrics(gateway, metrics)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path("gateways" / Segment / "routes" / Segment / Segment) { (gateway, route, metrics) ⇒
        pathEndOrSingleSlash {
          onSuccess(routeMetrics(gateway, route, metrics)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path("deployments" / Segment / "clusters" / Segment / "ports" / Segment / Segment) { (deployment, cluster, port, metrics) ⇒
        pathEndOrSingleSlash {
          onSuccess(clusterMetrics(deployment, cluster, port, metrics)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path("deployments" / Segment / "clusters" / Segment / "services" / Segment / "ports" / Segment / Segment) { (deployment, cluster, service, port, metrics) ⇒
        pathEndOrSingleSlash {
          onSuccess(serviceMetrics(deployment, cluster, service, port, metrics)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      } ~ path("deployments" / Segment / "clusters" / Segment / "services" / Segment / "instances" / Segment / "ports" / Segment / Segment) { (deployment, cluster, service, instance, port, metrics) ⇒
        pathEndOrSingleSlash {
          onSuccess(instanceMetrics(deployment, cluster, service, instance, port, metrics)) {
            case Some(result) ⇒ respondWith(OK, result)
            case _            ⇒ respondWith(NotFound, None)
          }
        }
      }
    }
  }
}
