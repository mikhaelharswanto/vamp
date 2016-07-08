package io.vamp.model.serialization

import io.vamp.common.json.SerializationFormat
import io.vamp.model.artifact._
import org.json4s.JsonAST.{ JObject, JString }
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object BlueprintSerializationFormat extends io.vamp.common.json.SerializationFormat {

  override def customSerializers = super.customSerializers :+
    new BlueprintSerializer() :+
    new ScaleSerializer() :+
    new ArgumentSerializer

  override def fieldSerializers = super.fieldSerializers :+
    new ClusterFieldSerializer() :+
    new ServiceFieldSerializer()
}

class BlueprintSerializer extends ArtifactSerializer[Blueprint] with TraitDecomposer with ReferenceSerialization with BlueprintGatewaySerializer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case blueprint: BlueprintReference ⇒ serializeReference(blueprint)
    case blueprint: AbstractBlueprint ⇒
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(blueprint.name))
      list += JField("gateways", serializeGateways(blueprint.gateways))
      list += JField("clusters", Extraction.decompose(blueprint.clusters.map(cluster ⇒ cluster.name -> cluster).toMap))
      list += JField("environment_variables", traits(blueprint.environmentVariables))
      new JObject(list.toList)
  }
}

class ClusterFieldSerializer extends ArtifactFieldSerializer[AbstractCluster] with DialectSerializer with InnerGatewaySerializer {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("name", _)            ⇒ None
    case ("gateways", gateways) ⇒ Some(("gateways", serializeGateways(gateways.asInstanceOf[List[Gateway]])))
    case ("dialects", dialects) ⇒ Some(("dialects", serializeDialects(dialects.asInstanceOf[Map[Dialect.Value, Any]])))
  }
}

class ServiceFieldSerializer extends ArtifactFieldSerializer[AbstractService] with ArgumentListSerializer with DialectSerializer with TraitDecomposer with BlueprintScaleSerializer {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = {
    case ("environmentVariables", environmentVariables) ⇒ Some(("environment_variables", traits(environmentVariables.asInstanceOf[List[Trait]])))
    case ("arguments", arguments)                       ⇒ Some(("arguments", serializeArguments(arguments.asInstanceOf[List[Argument]])))
    case ("dialects", dialects)                         ⇒ Some(("dialects", serializeDialects(dialects.asInstanceOf[Map[Dialect.Value, Any]])))
    case ("scale", Some(scale: Scale))                  ⇒ Some(("scale", serializerScale(scale, full = false)))
  }
}

class ScaleSerializer extends ArtifactSerializer[Scale] with BlueprintScaleSerializer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case scale: Scale ⇒ serializerScale(scale)
  }
}

trait BlueprintScaleSerializer extends ReferenceSerialization {
  def serializerScale(scale: Scale, full: Boolean = true): JObject = scale match {
    case scale: ScaleReference ⇒ serializeReference(scale)
    case scale: DefaultScale ⇒
      val list = new ArrayBuffer[JField]
      if (scale.name.nonEmpty && full)
        list += JField("name", JString(scale.name))
      list += JField("cpu", JDouble(scale.cpu.normalized.toDouble))
      list += JField("memory", JString(scale.memory.normalized))
      list += JField("instances", JInt(scale.instances))
      new JObject(list.toList)
  }
}

trait ArgumentListSerializer {
  def serializeArguments(arguments: List[Argument]) = Extraction.decompose(arguments)(SerializationFormat(BlueprintSerializationFormat))
}

trait DialectSerializer {
  def serializeDialects(dialects: Map[Dialect.Value, Any]) = Extraction.decompose(dialects.map({ case (k, v) ⇒ k.toString.toLowerCase -> v }))(DefaultFormats)
}

trait BlueprintGatewaySerializer extends GatewayDecomposer {
  def serializeGateways(gateways: List[Gateway]) = Extraction.decompose {
    gateways.map(gateway ⇒ gateway.port.name -> serializeAnonymousGateway(CoreSerializationFormat.default)(gateway)).toMap
  }(DefaultFormats)
}

trait InnerGatewaySerializer extends GatewayDecomposer {
  def serializeGateways(gateways: List[Gateway]) = Extraction.decompose {
    gateways.map(gateway ⇒ gateway.port.name -> serializeAnonymousGateway(CoreSerializationFormat.default)(gateway)).toMap
  }(DefaultFormats)
}
