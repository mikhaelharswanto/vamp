package io.vamp.rest_api

import akka.actor.ActorRef
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.config.Config
import io.vamp.common.http.RestApiBase
import io.vamp.common.http.SseDirectives._
import io.vamp.operation.controller.EventApiController
import spray.http.StatusCodes._

trait EventApiRoute extends EventApiController {
  this: CommonSupportForActors with RestApiBase ⇒

  implicit def timeout: Timeout

  val sseKeepAliveTimeout = Config.duration("vamp.rest-api.sse.keep-alive-timeout")

  val eventRoutes = pathPrefix("events") {
    pathEndOrSingleSlash {
      post {
        entity(as[String]) { request ⇒
          onSuccess(publish(request)) { result ⇒
            respondWith(Created, result)
          }
        }
      } ~ get {
        pageAndPerPage() { (page, perPage) ⇒
          parameterMultiMap { parameters ⇒
            entity(as[String]) { request ⇒
              onSuccess(query(parameters, request)(page, perPage)) { response ⇒
                respondWith(OK, response)
              }
            }
          }
        }
      }
    } ~ path("get") {
      pathEndOrSingleSlash {
        post {
          pageAndPerPage() { (page, perPage) ⇒
            entity(as[String]) { request ⇒
              onSuccess(query(Map(), request)(page, perPage)) { response ⇒
                respondWith(OK, response)
              }
            }
          }
        }
      }
    }
  }

  val sseRoutes = path("events" / "stream") {
    pathEndOrSingleSlash {
      get {
        parameterMultiMap { parameters ⇒
          entity(as[String]) { request ⇒
            sse { channel ⇒ openStream(channel, parameters, request) }
          }
        }
      }
    }
  }

  override def openStream(channel: ActorRef, parameters: Map[String, List[String]], request: String) = {
    log.debug("SSE connection open.")
    registerClosedHandler(channel, { () ⇒
      closeStream(channel)
      log.debug("SSE connection closed.")
    })

    super.openStream(channel, parameters, request)
  }
}
