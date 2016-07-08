package io.vamp.persistence.kv

import java.net.URLEncoder

import io.vamp.common.config.Config
import io.vamp.common.http.RestClient

import scala.concurrent.Future

class EtcdStoreActor extends KeyValueStoreActor {

  private val url = Config.string("vamp.persistence.key-value-store.etcd.url")

  override protected def info(): Future[Any] = for {
    version ← RestClient.get[Any](s"$url/version")
    self ← RestClient.get[Any](s"$url/v2/stats/self")
    store ← RestClient.get[Any](s"$url/v2/stats/store")
  } yield Map(
    "type" -> "etcd",
    "etcd" -> Map(
      "version" -> version,
      "statistics" -> self,
      "store" -> store
    )
  )

  override protected def get(path: List[String]): Future[Option[String]] = {
    RestClient.get[Any](urlOf(path), RestClient.jsonHeaders, logError = false) recover { case _ ⇒ None } map {
      case map: Map[_, _] ⇒ map.asInstanceOf[Map[String, _]].get("node").map(_.asInstanceOf[Map[String, _]]).getOrElse(Map()).get("value").map(_.toString)
      case _              ⇒ None
    }
  }

  override protected def set(path: List[String], data: Option[String]): Future[Any] = data match {
    case None        ⇒ RestClient.delete(urlOf(path), RestClient.jsonHeaders, logError = false)
    case Some(value) ⇒ RestClient.put[Any](urlOf(path), s"value=${URLEncoder.encode(value, "UTF-8")}", List("Accept" -> "application/json", "Content-Type" -> "application/x-www-form-urlencoded"))
  }

  private def urlOf(path: List[String], recursive: Boolean = false) = s"$url/v2/keys${KeyValueStoreActor.pathToString(path)}${if (recursive) "?recursive=true" else ""}"
}
