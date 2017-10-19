package io.vamp.persistence.db

import akka.pattern.ask
import io.vamp.common.akka.IoC
import io.vamp.model.artifact._
import io.vamp.persistence.kv.KeyValueStoreActor

import scala.concurrent.Future

class KeyValuePersistenceActor extends PersistenceActor with PersistenceMarshaller with TypeOfArtifact {

  protected def info(): Future[Any] = Future.successful(Map("type" -> "key-value"))

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope] = {
    val fromCache = cache.all(`type`, page, perPage)
    if (cacheEnabled && !fromCache.response.isEmpty) {
      return Future.successful(fromCache)
    }

    val as = type2string(`type`)

    log.debug(s"${getClass.getSimpleName}: all [$as] of $page per $perPage")

    checked[List[String]](IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.All(as :: Nil)) flatMap { list ⇒
      val from = (page - 1) * perPage

      if (from < 0 || from >= list.size) {
        Future.successful(ArtifactResponseEnvelope(Nil, 0, page, perPage))
      } else {
        val until = if (from + perPage >= list.size) list.size else from + perPage

        Future.sequence {
          list.slice(from, until).map {
            name ⇒ checked[Option[String]](IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(as :: name :: Nil))
          }
        } map {
          artifacts ⇒ {
            cache.setTotal(`type`, list.size)
            ArtifactResponseEnvelope(artifacts.flatten.flatMap(unmarshall(as, _) map (cache.set(_))), list.size, page, perPage)
          }
        }
      }
    }
  }

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]] = {
    if (cacheEnabled) {
      val fromCache = cache.read(name, `type`)
      if (fromCache.isDefined) {
        return Future.successful(cache.read(name, `type`))
      }
    }

    val as = type2string(`type`)
    log.debug(s"${getClass.getSimpleName}: read [$as] - $name}")

    checked[Option[String]](IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(as :: name :: Nil)) map {
      case Some(response) ⇒ unmarshall(as, response)
      case _              ⇒ None
    }
  }

  protected def set(artifact: Artifact): Future[Artifact] = {
    val json = marshall(artifact)
    val as = type2string(artifact.getClass)
    log.debug(s"${getClass.getSimpleName}: set [$as] - $json")
    if (cacheEnabled) {
      cache.set(artifact)
    }
    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set(as :: artifact.name :: Nil, Option(json)) map (_ ⇒ artifact)
  }

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Future[Boolean] = {
    val as = type2string(`type`)
    log.debug(s"${getClass.getSimpleName}: delete [$as] - $name}")
    if (cacheEnabled) {
      cache.delete(name, `type`)
    }
    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set(as :: name :: Nil, None) map (_ ⇒ true)
  }
}
