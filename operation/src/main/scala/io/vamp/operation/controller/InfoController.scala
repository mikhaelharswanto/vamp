package io.vamp.operation.controller

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ ActorSystemProvider, DataRetrieval, ExecutionContextProvider }
import io.vamp.common.config.Config
import io.vamp.common.vitals.{ InfoRequest, JmxVitalsProvider, JvmInfoMessage, JvmVitals }
import io.vamp.container_driver.ContainerDriverActor
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.model.Model
import io.vamp.persistence.db.PersistenceActor
import io.vamp.persistence.kv.KeyValueStoreActor
import io.vamp.pulse.PulseActor
import io.vamp.workflow_driver.WorkflowDriverActor

import scala.concurrent.Future

case class InfoMessage(message: String,
                       version: String,
                       uuid: String,
                       runningSince: String,
                       jvm: JvmVitals,
                       persistence: Any,
                       keyValue: Any,
                       pulse: Any,
                       gatewayDriver: Any,
                       containerDriver: Any,
                       workflowDriver: Any) extends JvmInfoMessage

trait InfoController extends DataRetrieval with JmxVitalsProvider {
  this: ExecutionContextProvider with ActorSystemProvider ⇒

  implicit def timeout: Timeout

  val infoMessage = Config.string("vamp.info.message")

  private val dataRetrievalTimeout = Config.timeout("vamp.info.timeout")

  def info: Future[JvmInfoMessage] = {

    val actors = List(
      classOf[PersistenceActor],
      classOf[KeyValueStoreActor],
      classOf[PulseActor],
      classOf[GatewayDriverActor],
      classOf[ContainerDriverActor],
      classOf[WorkflowDriverActor]
    ) map {
        _.asInstanceOf[Class[Actor]]
      }

    retrieve(actors, actor ⇒ actorFor(actor) ? InfoRequest, dataRetrievalTimeout) map { result ⇒
      InfoMessage(infoMessage,
        Model.version,
        Model.uuid,
        Model.runningSince,
        jvmVitals(),
        result.get(classOf[PersistenceActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[KeyValueStoreActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[PulseActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[GatewayDriverActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[ContainerDriverActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[WorkflowDriverActor].asInstanceOf[Class[Actor]])
      )
    }
  }
}
