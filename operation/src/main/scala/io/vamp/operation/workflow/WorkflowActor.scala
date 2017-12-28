package io.vamp.operation.workflow

import akka.actor._
import akka.pattern.ask
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.config.Config
import io.vamp.model.artifact.DefaultScale
import io.vamp.model.event.Event
import io.vamp.model.reader.{ MegaByte, Quantity }
import io.vamp.model.workflow._
import io.vamp.operation.OperationBootstrap
import io.vamp.operation.notification._
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.persistence.kv.KeyValueStoreActor
import io.vamp.pulse.Percolator.{ RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.{ PulseActor, PulseEventTags }
import io.vamp.workflow_driver.{ WorkflowDriver, WorkflowDriverActor }

import scala.concurrent.Future

object WorkflowActor {

  private val config = Config.config("vamp.operation.workflow")

  val command = config.stringList("command")

  val containerImage = config.string("container-image")

  val environmentVariables = config.entries("environment-variables").mapValues(_.toString)

  val scale = config.config("scale") match {
    case c ⇒ DefaultScale("", Quantity.of(c.double("cpu")), MegaByte.of(c.string("memory")), c.int("instances"))
  }

  object RescheduleAll

  case class Schedule(workflow: ScheduledWorkflow)

  case class Unschedule(workflow: ScheduledWorkflow)

  case class RunWorkflow(workflow: ScheduledWorkflow)

}

class WorkflowActor extends ArtifactPaginationSupport with ArtifactSupport with CommonSupportForActors with OperationNotificationProvider {

  import PulseEventTags.Workflows._
  import WorkflowActor._

  private val percolator = "workflow://"

  implicit val timeout = WorkflowDriverActor.timeout

  def receive: Receive = {

    case RescheduleAll ⇒ try reschedule() catch {
      case t: Throwable ⇒ reportException(WorkflowSchedulingError(t))
    }

    case Schedule(workflow) ⇒ try schedule(workflow) catch {
      case t: Throwable ⇒ reportException(WorkflowSchedulingError(t))
    }

    case Unschedule(workflow) ⇒ try unschedule(workflow) catch {
      case t: Throwable ⇒ reportException(WorkflowSchedulingError(t))
    }

    case (RunWorkflow(workflow), event: Event) ⇒ try trigger(workflow, event.tags) catch {
      case t: Throwable ⇒ reportException(WorkflowExecutionError(t))
    }

    case _ ⇒
  }

  override def preStart() = {
    try {
      context.system.scheduler.scheduleOnce(OperationBootstrap.synchronizationInitialDelay, self, RescheduleAll)
    } catch {
      case t: Throwable ⇒ reportException(InternalServerError(t))
    }
  }

  private def reschedule() = {
    implicit val timeout = PersistenceActor.timeout
    allArtifacts[ScheduledWorkflow] map {
      case workflows: List[_] ⇒ workflows.foreach(workflow ⇒ self ! Schedule(workflow))
      case any                ⇒ reportException(InternalServerError(any))
    }
  }

  private def schedule(workflow: ScheduledWorkflow): Unit = {
    log.info(s"Scheduling workflow: '${workflow.name}'.")

    workflow.trigger match {
      case DaemonTrigger           ⇒ trigger(workflow)
      case TimeTrigger(_, _, _)    ⇒ trigger(workflow)
      case EventTrigger(tags)      ⇒ IoC.actorFor[PulseActor] ! RegisterPercolator(s"$percolator${workflow.name}", tags, RunWorkflow(workflow))
      case DeploymentTrigger(name) ⇒ IoC.actorFor[PulseActor] ! RegisterPercolator(s"$percolator${workflow.name}", Set("deployments", s"deployments:$name"), RunWorkflow(workflow))
      case trigger                 ⇒ log.warning(s"Unsupported trigger: '$trigger'.")
    }

    pulse(workflow, scheduled = true)
  }

  private def unschedule(workflow: ScheduledWorkflow) = {
    log.info(s"Unscheduling workflow: '${workflow.name}'.")

    IoC.actorFor[PulseActor] ! UnregisterPercolator(s"$percolator${workflow.name}")
    IoC.actorFor[WorkflowDriverActor] ? WorkflowDriverActor.Unschedule(workflow) foreach { _ ⇒ pulse(workflow, scheduled = false) }
  }

  private def trigger(scheduledWorkflow: ScheduledWorkflow, data: Any = None) = for {
    workflow ← artifactFor[DefaultWorkflow](scheduledWorkflow.workflow)
    scale ← if (scheduledWorkflow.scale.isDefined) artifactFor[DefaultScale](scheduledWorkflow.scale.get) else Future.successful(WorkflowActor.scale)
  } yield {

    val expandedWorkflow = workflow.copy(
      command = Option(workflow.command.getOrElse(WorkflowActor.command.mkString(" "))),
      containerImage = Option(workflow.containerImage.getOrElse(WorkflowActor.containerImage)),
      environmentVariables = Option(workflow.environmentVariables.getOrElse(WorkflowActor.environmentVariables))
    )

    val path = WorkflowDriver.path(scheduledWorkflow, workflow = true)

    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set(path, expandedWorkflow.script) map {
      case _ ⇒ IoC.actorFor[WorkflowDriverActor] ! WorkflowDriverActor.Schedule(scheduledWorkflow.copy(workflow = expandedWorkflow, scale = Option(scale)), data)
    }
  }

  private def pulse(workflow: ScheduledWorkflow, scheduled: Boolean) = {
    actorFor[PulseActor] ! Publish(Event(Set(s"workflows${Event.tagDelimiter}${workflow.name}", if (scheduled) scheduledTag else unscheduledTag), content = workflow), publishEventValue = false)
  }
}
