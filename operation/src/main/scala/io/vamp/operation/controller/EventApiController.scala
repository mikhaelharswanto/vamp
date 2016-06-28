package io.vamp.operation.controller

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.event.{ EventQuery, TimeRange }
import io.vamp.model.reader._
import io.vamp.operation.sse.EventStreamingActor
import io.vamp.operation.sse.EventStreamingActor.{ CloseStream, OpenStream }
import io.vamp.pulse.PulseActor.{ Publish, Query }
import io.vamp.pulse.{ EventRequestEnvelope, EventResponseEnvelope, PulseActor }

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.{ existentials, postfixOps }

trait EventApiController {
  this: ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  def publish(request: String)(implicit timeout: Timeout) = {
    val event = EventReader.read(request)
    actorFor[PulseActor] ? Publish(event) map (_ ⇒ event)
  }

  def query(request: String)(page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = {
    actorFor[PulseActor] ? Query(EventRequestEnvelope(EventQueryReader.read(request), page, perPage))
  }

  def openStream(to: ActorRef, tags: Set[String]) = actorFor[EventStreamingActor] ! OpenStream(to, tags)

  def closeStream(to: ActorRef) = actorFor[EventStreamingActor] ! CloseStream(to)
}

trait EventValue {
  this: ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  implicit def timeout: Timeout

  def last(tags: Set[String], window: FiniteDuration): Future[Option[AnyRef]] = {

    val eventQuery = EventQuery(tags, Option(timeRange(window)), None)

    actorFor[PulseActor] ? PulseActor.Query(EventRequestEnvelope(eventQuery, 1, 1)) map {
      case env: EventResponseEnvelope ⇒ Option(env.response.head.value.asInstanceOf)
      case other                      ⇒ None
    }
  }

  protected def timeRange(window: FiniteDuration) = {

    val now = OffsetDateTime.now()
    val from = now.minus(window.toSeconds, ChronoUnit.SECONDS)

    TimeRange(Some(from), Some(now), includeLower = true, includeUpper = true)
  }
}
