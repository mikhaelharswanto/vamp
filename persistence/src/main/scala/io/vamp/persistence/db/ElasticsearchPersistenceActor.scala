package io.vamp.persistence.db

import io.vamp.common.config.Config
import io.vamp.model.artifact._
import io.vamp.model.reader._
import io.vamp.model.serialization.CoreSerializationFormat
import io.vamp.persistence.operation._
import io.vamp.pulse.ElasticsearchClient
import io.vamp.pulse.ElasticsearchClient.{ElasticsearchSearchResponse}
import org.json4s.native.Serialization._
import scala.concurrent.duration._

import scala.concurrent.Future

object ElasticsearchPersistenceActor {

  lazy val index = Config.string("vamp.persistence.database.elasticsearch.index")

  lazy val elasticsearchUrl: String = Config.string("vamp.persistence.database.elasticsearch.url")

  lazy val elasticsearcCacheRefreshPeriod: FiniteDuration = Config.duration("vamp.persistence.database.elasticsearch.cache.refresh-period")
}

case class ElasticsearchArtifact(artifact: String)

case class ElasticsearchPersistenceInfo(`type`: String, url: String, index: String, initializationTime: String, elasticsearch: Any)

class ElasticsearchPersistenceActor extends PersistenceActor with TypeOfArtifact with PaginationSupport {

  import ElasticsearchPersistenceActor._
  import YamlSourceReader._

  private val es = new ElasticsearchClient(elasticsearchUrl)

  private val cache = new InMemoryStore(log)

  actorSystem.scheduler.schedule(elasticsearcCacheRefreshPeriod, elasticsearcCacheRefreshPeriod, new Runnable {
    override def run(): Unit = cache.clear()
  })

  protected def info(): Future[Any] = for {
    health ← es.health
    initializationTime ← es.creationTime(index)
  } yield ElasticsearchPersistenceInfo("elasticsearch", elasticsearchUrl, index, initializationTime, health)

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope] = {
    log.debug(s"${getClass.getSimpleName}: all [${type2string(`type`)}] of $page per $perPage")
    val fromCache = cache.all(`type`, page, perPage)
    if (fromCache != Nil && fromCache.total > 0) {
      return Future.successful(fromCache)
    }
    val from = (page - 1) * perPage
    es.search[ElasticsearchSearchResponse](index, `type`,
      s"""
         |{
         |  "query": {
         |    "filtered": {
         |      "query": {
         |        "match_all": {}
         |      }
         |    }
         |  },
         |  "from": $from,
         |  "size": $perPage
         |}
        """.stripMargin) map {
      case response ⇒ ArtifactResponseEnvelope(response.hits.hits.flatMap { hit ⇒ read(`type`, hit._source) }, response.hits.total, from, perPage)
    }
  }

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]] = Future.successful {
    log.debug(s"${getClass.getSimpleName}: read [${`type`.getSimpleName}] - $name}")
    cache.read(name, `type`)
  }

  protected def set(artifact: Artifact): Future[Artifact] = {
    val json = write(artifact)(CoreSerializationFormat.full)
    log.debug(s"${getClass.getSimpleName}: set [${artifact.getClass.getSimpleName}] - $json")
    cache.set(artifact)
    es.index[Any](index, artifact.getClass, artifact.name, ElasticsearchArtifact(json)).map { _ ⇒ artifact }
  }

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Future[Boolean] = {
    log.debug(s"${getClass.getSimpleName}: delete [${`type`.getSimpleName}] - $name}")
    cache.delete(name, `type`).isDefined
    es.delete(index, `type`, name).map {
      response ⇒ response != None
    }
  }

  private def read(`type`: String, source: Map[String, Any]): Option[Artifact] = source.get("artifact").flatMap { artifact ⇒
    readerOf(`type`).flatMap { reader ⇒ Option(reader.read(artifact.toString)) } map {
      artifact ⇒ {
        cache.set(artifact)
        artifact
      }
    }
  }

  private def readerOf(`type`: String): Option[YamlReader[_ <: Artifact]] = Map(
    "gateways" -> DeployedGatewayReader,
    "deployments" -> new AbstractDeploymentReader() {
      override protected def routingReader = new InnerGatewayReader(acceptPort = true, onlyAnonymous = false)

      override protected def validateEitherReferenceOrAnonymous = false
    },
    "breeds" -> BreedReader,
    "blueprints" -> BlueprintReader,
    "slas" -> SlaReader,
    "scales" -> ScaleReader,
    "escalations" -> EscalationReader,
    "routes" -> RouteReader,
    "filters" -> ConditionReader,
    "conditions" -> ConditionReader,
    "rewrites" -> RewriteReader,
    "workflows" -> WorkflowReader,
    "scheduled-workflows" -> ScheduledWorkflowReader,
    // gateway persistence
    "route-targets" -> new NoNameValidationYamlReader[RouteTargets] {
      override protected def parse(implicit source: YamlSourceReader) = {
        val targets = <<?[YamlList]("targets") match {
          case Some(list) ⇒ list.flatMap {
            case yaml ⇒
              implicit val source = yaml
              (<<?[String]("name"), <<?[String]("url")) match {
                case (_, Some(url)) ⇒ ExternalRouteTarget(url) :: Nil
                case (Some(name), _) ⇒ InternalRouteTarget(name, <<?[String]("host"), <<![Int]("port")) :: Nil
                case _ ⇒ Nil
              }
          }
          case _ ⇒ Nil
        }
        RouteTargets(<<![String]("name"), targets)
      }
    },
    "gateway-ports" -> new NoNameValidationYamlReader[GatewayPort] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayPort(name, <<![Int]("port"))
    },
    "gateway-services" -> new NoNameValidationYamlReader[GatewayServiceAddress] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayServiceAddress(name, <<![String]("host"), <<![Int]("port"))
    },
    "gateway-deployment-statuses" -> new NoNameValidationYamlReader[GatewayDeploymentStatus] {
      override protected def parse(implicit source: YamlSourceReader) = GatewayDeploymentStatus(name, <<![Boolean]("deployed"))
    },
    "inner-gateway" -> new NoNameValidationYamlReader[InnerGateway] {
      override protected def parse(implicit source: YamlSourceReader) = {
        <<?[Any]("name")
        <<?[Any]("gateway" :: Lookup.entry :: Nil)
        InnerGateway(DeployedGatewayReader.read(<<![YamlSourceReader]("gateway")))
      }
    },
    // deployment persistence
    "deployment-service-states" -> new NoNameValidationYamlReader[DeploymentServiceState] {
      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceState(name, DeploymentServiceStateReader.read(<<![YamlSourceReader]("state")))
    },
    "deployment-service-instances" -> new NoNameValidationYamlReader[DeploymentServiceInstances] {
      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceInstances(name, DeploymentReader.parseInstances)
    },
    "deployment-service-environment-variables" -> new NoNameValidationYamlReader[DeploymentServiceEnvironmentVariables] {

      override protected def parse(implicit source: YamlSourceReader) = DeploymentServiceEnvironmentVariables(name, environmentVariables)

      private def environmentVariables(implicit source: YamlSourceReader): List[EnvironmentVariable] = first[Any]("environment_variables", "env") match {
        case Some(list: List[_]) ⇒ list.map { el ⇒
          implicit val source = el.asInstanceOf[YamlSourceReader]
          EnvironmentVariable(<<![String]("name"), <<?[String]("alias"), <<?[String]("value"), <<?[String]("interpolated"))
        }
        case _ ⇒ Nil
      }
    }
  ).get(`type`)
}

trait NoNameValidationYamlReader[T] extends YamlReader[T] {

  import YamlSourceReader._

  override protected def name(implicit source: YamlSourceReader): String = <<![String]("name")
}
