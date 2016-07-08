package io.vamp.model.reader

import io.vamp.model.artifact._
import io.vamp.model.notification._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BlueprintReaderSpec extends FlatSpec with Matchers with ReaderSpec {

  "BlueprintReader" should "read the simplest YAML (name and single breed only)" in {
    BlueprintReader.read(res("blueprint/blueprint1.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the gateways and parameters" in {
    BlueprintReader.read(res("blueprint/blueprint2.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), Nil, None))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", "notorious/port", None, None, Nil, Nil, None))))),
      'environmentVariables(List(EnvironmentVariable("notorious.environment_variables.aspect", None, Some("thorium"))))
    )
  }

  it should "read the reference sla" in {
    BlueprintReader.read(res("blueprint/blueprint3.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), Nil, Some(SlaReference("strong-mountain", Nil))))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference sla with explicit name" in {
    BlueprintReader.read(res("blueprint/blueprint4.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), Nil, Some(SlaReference("strong-mountain", Nil))))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference sla with escalations" in {
    BlueprintReader.read(res("blueprint/blueprint5.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), Nil, Some(SlaReference("strong-mountain", List(ToAllEscalation("", List(EscalationReference("red-flag"), EscalationReference("hideous-screaming"), GenericEscalation("", "cloud-beam", Map("sound" -> "furious")))))))))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the anonymous sla" in {
    BlueprintReader.read(res("blueprint/blueprint6.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), Nil, Some(GenericSla("", "vital-cloud", Nil, Map("reborn" -> "red-swallow")))))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the anonymous sla with escalations" in {
    BlueprintReader.read(res("blueprint/blueprint7.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), Nil, Some(GenericSla("", "vital-cloud", List(EscalationReference("red-flag")), Map("reborn" -> "red-swallow")))))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the anonymous scale" in {
    BlueprintReader.read(res("blueprint/blueprint8.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, Some(DefaultScale("", Quantity(0.2), MegaByte(120), 2)), Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference scale" in {
    BlueprintReader.read(res("blueprint/blueprint9.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, Some(ScaleReference("large")), Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference scale with explicit name parameter" in {
    BlueprintReader.read(res("blueprint/blueprint10.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, Some(ScaleReference("large")), Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the reference route" in {
    BlueprintReader.read(res("blueprint/blueprint11.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), List(Gateway("", Port("", None, None), None, None, Nil, List(RouteReference("conservative", "nocturnal-viper")))), None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the route with weight" in {
    BlueprintReader.read(res("blueprint/blueprint12.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), List(Gateway("", Port("", None, None), None, None, Nil, List(DefaultRoute("", "nocturnal-viper", Some(Percentage(100)), None, Nil, Nil, None)))), None, Map()))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the route with filter reference" in {
    BlueprintReader.read(res("blueprint/blueprint13.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), List(Gateway("", Port("", None, None), None, None, Nil, List(DefaultRoute("", "nocturnal-viper", None, None, List(ConditionReference("android")), Nil, None)))), None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the route with filter references" in {
    BlueprintReader.read(res("blueprint/blueprint14.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), List(Gateway("", Port("", None, None), None, None, Nil, List(DefaultRoute("", "nocturnal-viper", None, None, List(ConditionReference("android"), ConditionReference("ios")), Nil, None)))), None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read the route with anonymous filter" in {
    BlueprintReader.read(res("blueprint/blueprint15.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), List(Gateway("", Port("", None, None), None, None, Nil, List(DefaultRoute("", "nocturnal-viper", Some(Percentage(100)), None, List(DefaultCondition("", "user.agent != ios")), Nil, None)))), None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "fail on both reference and inline route declarations" in {
    expectedError[EitherReferenceOrAnonymous]({
      BlueprintReader.read(res("blueprint/blueprint16.yml"))
    }) should have(
      'name("route"),
      'reference("!ios")
    )
  }

  it should "expand the filter list" in {
    BlueprintReader.read(res("blueprint/blueprint17.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), List(Gateway("", Port("", None, None), None, None, Nil, List(DefaultRoute("", "nocturnal-viper", None, None, List(ConditionReference("android")), Nil, None)))), None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the breed" in {
    BlueprintReader.read(res("blueprint/blueprint18.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the services" in {
    BlueprintReader.read(res("blueprint/blueprint19.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the cluster" in {
    BlueprintReader.read(res("blueprint/blueprint20.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the more complex blueprint" in {
    BlueprintReader.read(res("blueprint/blueprint21.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), Nil, Some(DefaultScale("", Quantity(0.2), MegaByte(120), 2)), Nil), Service(BreedReference("remote-venus"), Nil, Some(ScaleReference("worthy")), Nil)), List(Gateway("", Port("", None, None), None, None, Nil, List(DefaultRoute("", "solid-barbershop", Some(Percentage(100)), None, List(DefaultCondition("", "ua = android")), Nil, None)))), Some(GenericSla("", "vital-cloud", List(ToAllEscalation("", List(EscalationReference("red-flag"), EscalationReference("hideous-screaming"), GenericEscalation("", "cloud-beam", Map("sound" -> "furious"))))), Map("reborn" -> "red-swallow")))), Cluster("notorious", List(Service(DefaultBreed("nocturnal-viper", Deployable("anaconda"), Nil, Nil, Nil, Nil, Map()), Nil, None, Nil)), Nil, None), Cluster("needless", List(Service(DefaultBreed("hideous-canal", Deployable("old/crystal"), Nil, Nil, Nil, Nil, Map()), Nil, None, Nil)), Nil, Some(SlaReference("fish-steamy", Nil))), Cluster("omega", List(Service(BreedReference("scary-lion"), Nil, None, Nil)), Nil, None))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", "supersonic/port", None, None, Nil, Nil, None))))),
      'environmentVariables(List(EnvironmentVariable("omega.environment_variables.aspect", None, Some("thorium"))))
    )
  }

  it should "validate gateways for inline breeds - valid case" in {
    BlueprintReader.read(res("blueprint/blueprint22.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), List(Port("port", None, Some("80/http"))), Nil, Nil, Nil, Map()), Nil, None, Nil)), Nil, None))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", "supersonic/port", None, None, Nil, Nil, None))))),
      'environmentVariables(Nil)
    )
  }

  it should "validate gateways for inline breeds - no cluster" in {
    expectedError[UnresolvedGatewayPortError]({
      BlueprintReader.read(res("blueprint/blueprint23.yml"))
    }) should have(
      'name("omega.port"),
      'value(Some("8080"))
    )
  }

  it should "validate gateways for inline breeds - not a port" in {
    expectedError[UnresolvedGatewayPortError]({
      BlueprintReader.read(res("blueprint/blueprint24.yml"))
    }) should have(
      'name("supersonic.port"),
      'value(Some("8080"))
    )
  }

  it should "validate gateways for inline breeds - no port" in {
    expectedError[UnresolvedGatewayPortError]({
      BlueprintReader.read(res("blueprint/blueprint25.yml"))
    }) should have(
      'name("supersonic.http"),
      'value(Some("8080"))
    )
  }

  it should "validate environment variables for inline breeds - valid case" in {
    BlueprintReader.read(res("blueprint/blueprint26.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), Nil, List(EnvironmentVariable("port", None, None)), Nil, Nil, Map()), Nil, None, Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(List(EnvironmentVariable("supersonic.environment_variables.port", None, Some("8080"))))
    )
  }

  it should "validate environment variables for inline breeds - no cluster" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint27.yml"))
    }) should have(
      'name("omega.port"),
      'value("8080")
    )
  }

  it should "validate environment variables for inline breeds - not a trait" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint28.yml"))
    }) should have(
      'name("supersonic.port"),
      'value("8080")
    )
  }

  it should "validate environment variables for inline breeds - no trait" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint29.yml"))
    }) should have(
      'name("supersonic.http"),
      'value("8080")
    )
  }

  it should "validate environment variables for setting port values" in {
    expectedError[UnresolvedEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint30.yml"))
    }) should have(
      'name("supersonic.port"),
      'value("8080")
    )
  }

  it should "validate breed uniqueness across clusters" in {
    expectedError[NonUniqueBlueprintBreedReferenceError]({
      BlueprintReader.read(res("blueprint/blueprint31.yml"))
    }) should have(
      'name("solid-barbershop")
    )
  }

  it should "validate breed uniqueness across services" in {
    expectedError[NonUniqueBlueprintBreedReferenceError]({
      BlueprintReader.read(res("blueprint/blueprint32.yml"))
    }) should have(
      'name("solid-barbershop")
    )
  }

  it should "validate breed cross dependencies - no inline" in {
    BlueprintReader.read(res("blueprint/blueprint33.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), Nil, None, Nil)), Nil, None), Cluster("notorious", List(Service(BreedReference("elastic-search"), Nil, None, Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "validate breed cross dependencies - inline and valid" in {
    BlueprintReader.read(res("blueprint/blueprint34.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("solid/barbershop"), Nil, Nil, Nil, Nil, Map("es" -> BreedReference("elastic-search"))), Nil, None, Nil)), Nil, None), Cluster("notorious", List(Service(BreedReference("elastic-search"), Nil, None, Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "validate breed cross dependencies - missing reference for an inline breed" in {
    expectedError[UnresolvedBreedDependencyError]({
      BlueprintReader.read(res("blueprint/blueprint35.yml"))
    }) should have(
      'breed(DefaultBreed("solid-barbershop", Deployable("solid/barbershop"), Nil, Nil, Nil, Nil, Map("es" -> BreedReference("elastic-search")))),
      'dependency("es" -> BreedReference("elastic-search"))
    )
  }

  it should "expand the service with breed only object" in {
    BlueprintReader.read(res("blueprint/blueprint36.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("donut"), Nil, Nil, Nil, Nil, Map()), Nil, None, Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand the service with breed reference only object" in {
    BlueprintReader.read(res("blueprint/blueprint37.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), Nil, None, Nil)), Nil, None))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and route - expanded" in {
    BlueprintReader.read(res("blueprint/blueprint38.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("sava1", Deployable("magneticio/sava:latest"), List(Port("port", None, Some("80/http"))), Nil, Nil, Nil, Map()), Nil, Some(DefaultScale("", Quantity(0.5), MegaByte(512), 1)), Nil)), List(Gateway("", Port("port", None, None), None, None, Nil, List(DefaultRoute("", "sava1", None, None, List(ConditionReference("android")), Nil, None)))), None))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", "supersonic/port", None, None, Nil, Nil, None))))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and route - service single element." in {
    BlueprintReader.read(res("blueprint/blueprint39.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("sava1", Deployable("magneticio/sava:latest"), List(Port("port", None, Some("80/http"))), Nil, Nil, Nil, Map()), Nil, Some(DefaultScale("", Quantity(0.5), MegaByte(512), 1)), Nil)), List(Gateway("", Port("port", None, None), None, None, Nil, List(DefaultRoute("", "sava1", None, None, List(ConditionReference("android")), Nil, None)))), None))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", "supersonic/port", None, None, Nil, Nil, None))))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and route - no service just cluster." in {
    BlueprintReader.read(res("blueprint/blueprint40.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("sava1", Deployable("magneticio/sava:latest"), List(Port("port", None, Some("80/http"))), Nil, Nil, Nil, Map()), Nil, Some(DefaultScale("", Quantity(0.5), MegaByte(512), 1)), Nil)), List(Gateway("", Port("port", None, None), None, None, Nil, List(DefaultRoute("", "sava1", None, None, List(ConditionReference("android")), Nil, None)))), None))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", "supersonic/port", None, None, Nil, Nil, None))))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and route - no service and compact breed." in {
    BlueprintReader.read(res("blueprint/blueprint41.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("sava1"), Nil, Some(ScaleReference("large")), Nil)), List(Gateway("", Port("", None, None), None, None, Nil, List(DefaultRoute("", "sava1", None, None, List(ConditionReference("android")), Nil, None)))), None))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", "supersonic/port", None, None, Nil, Nil, None))))),
      'environmentVariables(Nil)
    )
  }

  it should "read scale and route - cluster contains list." in {
    BlueprintReader.read(res("blueprint/blueprint42.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("sava1"), Nil, Some(ScaleReference("large")), Nil)), Nil, None))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", "supersonic/port", None, None, Nil, Nil, None))))),
      'environmentVariables(Nil)
    )
  }

  it should "fail on direct recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BlueprintReader.read(res("blueprint/blueprint43.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), Nil, Nil, Nil, Nil, Map("db" -> BreedReference("monarch"))))
    )
  }

  it should "fail on indirect recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BlueprintReader.read(res("blueprint/blueprint44.yml"))
    }) should have(
      'breed(DefaultBreed("monarch2", Deployable("magneticio/monarch2:latest"), Nil, Nil, Nil, Nil, Map("db" -> BreedReference("monarch1"))))
    )
  }

  it should "expand single reference filter to a list" in {
    BlueprintReader.read(res("blueprint/blueprint45.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("sava1"), Nil, None, Nil)), List(Gateway("", Port("", None, None), None, None, Nil, List(DefaultRoute("", "sava1", None, None, List(ConditionReference("android")), Nil, None)))), None))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", "supersonic/port", None, None, Nil, Nil, None))))),
      'environmentVariables(Nil)
    )
  }

  it should "expand single filter to a list" in {
    BlueprintReader.read(res("blueprint/blueprint46.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("sava1"), Nil, Some(ScaleReference("large")), Nil)), List(Gateway("", Port("", None, None), None, None, Nil, List(DefaultRoute("", "sava1", None, None, List(DefaultCondition("", "user.agent == android")), Nil, None)))), None))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", "supersonic/port", None, None, Nil, Nil, None))))),
      'environmentVariables(Nil)
    )
  }

  it should "parse dialects" in {
    BlueprintReader.read(res("blueprint/blueprint47.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("sava1"), Nil, None, Nil, Map(Dialect.Marathon -> Map("a" -> "b"), Dialect.Docker -> Map("c" -> "d"))), Service(BreedReference("sava2"), Nil, None, Nil, Map())), Nil, None, Map(Dialect.Marathon -> Map("r" -> "t"), Dialect.Docker -> Map("q" -> "w", "o" -> "p"))), Cluster("viper", List(Service(BreedReference("sava3"), Nil, None, Nil, Map()), Service(BreedReference("sava4"), Nil, None, Nil, Map())), Nil, None, Map(Dialect.Marathon -> Map("u" -> "i"))))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "expand and parse dialects" in {
    BlueprintReader.read(res("blueprint/blueprint48.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("sava1"), Nil, None, Nil, Map(Dialect.Marathon -> Map("a" -> "b"), Dialect.Docker -> Map("c" -> "d"))), Service(BreedReference("sava2"), Nil, None, Nil, Map())), Nil, None, Map(Dialect.Marathon -> Map("r" -> "t"), Dialect.Docker -> Map("q" -> "w", "o" -> "p"))), Cluster("viper", List(Service(BreedReference("sava3"), Nil, None, Nil, Map()), Service(BreedReference("sava4"), Nil, None, Nil, Map())), Nil, None, Map(Dialect.Marathon -> Map("u" -> "i"))))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "not allow blueprint with no service" in {
    expectedError[NoServiceError.type]({
      BlueprintReader.read(res("blueprint/blueprint49.yml"))
    })
  }

  it should "read service environment variables" in {
    BlueprintReader.read(res("blueprint/blueprint50.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("docker://vamp/solid-barbershop"), Nil, List(EnvironmentVariable("HEAP", None, Some("1024MB"))), Nil, Nil, Map()), List(EnvironmentVariable("HEAP", None, Some("2GB"), None)), None, Nil, Map())), Nil, None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "read service environment variables for ref breed" in {
    BlueprintReader.read(res("blueprint/blueprint51.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("supersonic", List(Service(BreedReference("solid-barbershop"), List(EnvironmentVariable("HEAP", None, Some("2GB"), None)), None, Nil, Map())), Nil, None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "not allow service override of non existing breed environment variables" in {
    expectedError[UnresolvedDependencyInTraitValueError]({
      BlueprintReader.read(res("blueprint/blueprint52.yml"))
    }) should have(
      'breed(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), Nil, Nil, Nil, Nil, Map())),
      'reference("HEAP")
    )
  }

  it should "not allow an empty service level environment variable" in {
    expectedError[MissingEnvironmentVariableError]({
      BlueprintReader.read(res("blueprint/blueprint53.yml"))
    }) should have(
      'breed(DefaultBreed("solid-barbershop", Deployable("vamp/solid-barbershop"), Nil, List(EnvironmentVariable("HEAP", None, Some("1024MB"))), Nil, Nil, Map())),
      'name("HEAP")
    )
  }

  it should "process default port type" in {
    val blueprint = BlueprintReader.read(res("blueprint/blueprint54.yml")).asInstanceOf[DefaultBlueprint]

    blueprint should have(
      'name("nomadic-frostbite"),
      'gateways(List(Gateway("", Port("8081", None, Some("8081")), None, None, Nil, List(DefaultRoute("", "supersonic/port", None, None, Nil, Nil, None))), Gateway("", Port("8082", None, Some("8082/tcp")), None, None, Nil, List(DefaultRoute("", "supersonic/health", None, None, Nil, Nil, None))), Gateway("", Port("8083", None, Some("8083/http")), None, None, Nil, List(DefaultRoute("", "supersonic/metrics", None, None, Nil, Nil, None))))),
      'clusters(List(Cluster("supersonic", List(Service(DefaultBreed("solid-barbershop", Deployable("docker", Some("vamp/solid-barbershop")), List(Port("port", None, Some("80/http")), Port("health", None, Some("8080")), Port("metrics", None, Some("8090/tcp"))), Nil, Nil, Nil, Map()), Nil, None, Nil, Map())), Nil, None, Map())))
    )

    blueprint.gateways.foreach {
      case gateway: Gateway if gateway.routes.exists(_.path.normalized == "supersonic/port")    ⇒ gateway.port.`type` shouldBe Port.Type.Http
      case gateway: Gateway if gateway.routes.exists(_.path.normalized == "supersonic/health")  ⇒ gateway.port.`type` shouldBe Port.Type.Tcp
      case gateway: Gateway if gateway.routes.exists(_.path.normalized == "supersonic/metrics") ⇒ gateway.port.`type` shouldBe Port.Type.Http
    }

    blueprint.clusters.find(_.name == "supersonic") foreach {
      case cluster ⇒ cluster.services.find(service ⇒ service.breed.name == "solid-barbershop") foreach { service ⇒
        service.breed.asInstanceOf[DefaultBreed].ports.foreach {
          case port: Port if port.name == "port"    ⇒ port.`type` shouldBe Port.Type.Http
          case port: Port if port.name == "health"  ⇒ port.`type` shouldBe Port.Type.Http
          case port: Port if port.name == "metrics" ⇒ port.`type` shouldBe Port.Type.Tcp
        }
      }
    }
  }

  it should "report not supported dialect" in {
    expectedError[UnexpectedElement]({
      BlueprintReader.read(res("blueprint/blueprint55.yml"))
    }) should have(
      'element(Map("clusters" -> Map("supersonic" -> Map("services" -> List(Map("dialects" -> Map("google" -> Map("e" -> "f")))), "dialects" -> Map("google" -> Map("w" -> "e", "e" -> "r"))))))
    )
  }

  it should "report not supported element 'scala'" in {
    expectedError[UnexpectedElement]({
      BlueprintReader.read(res("blueprint/blueprint56.yml"))
    }) should have(
      'element(Map("clusters" -> Map("sava" -> Map("services" -> List(Map("scala" -> Map("cpu" -> 0.2, "memory" -> 128, "instances" -> 1)))))))
    )
  }

  it should "read sticky route" in {
    BlueprintReader.read(res("blueprint/blueprint57.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil, Map())), List(Gateway("", Port("", None, None), None, Some(Gateway.Sticky.Route), Nil, Nil)), None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "read sticky instance" in {
    BlueprintReader.read(res("blueprint/blueprint58.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil, Map())), List(Gateway("", Port("", None, None), None, Some(Gateway.Sticky.Instance), Nil, Nil)), None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "report illegal sticky value" in {
    expectedError[IllegalGatewayStickyValue]({
      BlueprintReader.read(res("blueprint/blueprint59.yml"))
    }) should have(
      'sticky("server")
    )
  }

  it should "report illegal cluster name" in {
    expectedError[IllegalName]({
      BlueprintReader.read(res("blueprint/blueprint60.yml"))
    }) should have(
      'name("notorious/snake")
    )
  }

  it should "parse multiple port routing" in {
    BlueprintReader.read(res("blueprint/blueprint61.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("sava", List(Service(DefaultBreed("sava_1.0", Deployable("docker", Some("magneticio/sava:1.0.0")), List(Port("web", None, Some("8080")), Port("admin", None, Some("8081"))), Nil, Nil, Nil, Map()), Nil, None, Nil, Map())), List(Gateway("", Port("web", None, None), None, Some(Gateway.Sticky.Route), Nil, Nil), Gateway("", Port("admin", None, None), None, Some(Gateway.Sticky.Instance), Nil, Nil)), None, Map()))),
      'gateways(Nil),
      'environmentVariables(Nil)
    )
  }

  it should "read sticky none" in {
    BlueprintReader.read(res("blueprint/blueprint62.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil, Map())), List(Gateway("", Port("", None, None), None, None, Nil, Nil)), None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "read sticky null" in {
    BlueprintReader.read(res("blueprint/blueprint63.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil, Map())), List(Gateway("", Port("", None, None), None, None, Nil, Nil)), None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "allow sticky http port" in {
    BlueprintReader.read(res("blueprint/blueprint64.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(DefaultBreed("nocturnal-viper", Deployable("docker", Some("anaconda")), List(Port("web", None, Some("8080/http"))), Nil, Nil, Nil, Map()), Nil, None, Nil, Map())), List(Gateway("", Port("web", None, None), None, Some(Gateway.Sticky.Route), Nil, Nil)), None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "not allow sticky tcp port" in {
    expectedError[StickyPortTypeError]({
      BlueprintReader.read(res("blueprint/blueprint65.yml"))
    }) should have(
      'port(Port("web", None, Some("8080/tcp")))
    )
  }

  it should "not allow HTTP conditions on tcp port" in {
    expectedError[ConditionPortTypeError]({
      BlueprintReader.read(res("blueprint/blueprint66.yml"))
    }) should have(
      'port(Port("web", None, Some("8080/tcp"))),
      'condition(DefaultCondition("", "user.agent != ios"))
    )
  }

  it should "not allow anonymous port mapping if more than 1 port defined" in {
    expectedError[IllegalAnonymousRoutingPortMappingError]({
      BlueprintReader.read(res("blueprint/blueprint67.yml"))
    }) should have(
      'breed(DefaultBreed("nocturnal-viper", Deployable("docker", Some("anaconda")), List(Port("web", None, Some("8080")), Port("admin", None, Some("9090"))), Nil, Nil, Nil, Map()))
    )
  }

  it should "remap anonymous routing port" in {
    BlueprintReader.read(res("blueprint/blueprint68.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(DefaultBreed("nocturnal-viper", Deployable("docker", Some("anaconda")), List(Port("web", None, Some("8080"))), Nil, Nil, Nil, Map()), Nil, None, Nil, Map())), List(Gateway("", Port("web", None, None), None, Some(Gateway.Sticky.Route), Nil, Nil)), None, Map()))),
      'environmentVariables(Nil)
    )
  }

  it should "read complex gateway" in {
    BlueprintReader.read(res("blueprint/blueprint69.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(DefaultBreed("nocturnal-viper", Deployable("docker", Some("anaconda")), List(Port("web", None, Some("9050")), Port("admin", None, Some("9060"))), Nil, Nil, Nil, Map()), Nil, None, Nil, Map())), List(Gateway("", Port("web", None, None), None, Some(Gateway.Sticky.Route), Nil, Nil)), None, Map()))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", GatewayPath("notorious/web", List("notorious", "web")), None, None, Nil, Nil, None))), Gateway("", Port("8081", None, Some("8081")), None, Some(Gateway.Sticky.Route), Nil, List(DefaultRoute("", GatewayPath("notorious/admin", List("notorious", "admin")), None, None, Nil, Nil, None))))))
  }

  it should "not allow sticky tcp gateway" in {
    expectedError[StickyPortTypeError]({
      BlueprintReader.read(res("blueprint/blueprint70.yml"))
    }) should have(
      'port(Port("8080/tcp", None, Some("8080/tcp")))
    )
  }

  it should "not allow HTTP conditions on gateway tcp port" in {
    expectedError[ConditionPortTypeError]({
      BlueprintReader.read(res("blueprint/blueprint71.yml"))
    }) should have(
      'port(Port("notorious/web", None, Some("8080/tcp"))),
      'condition(DefaultCondition("", "user.agent != ios"))
    )
  }

  it should "read complex gateway with weights" in {
    BlueprintReader.read(res("blueprint/blueprint72.yml")) should have(
      'name("nomadic-frostbite"),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", GatewayPath("notorious/port1", List("notorious", "port1")), Some(Percentage(50)), None, Nil, Nil, None), DefaultRoute("", GatewayPath("notorious/port2", List("notorious", "port2")), Some(Percentage(50)), None, Nil, Nil, None), DefaultRoute("", GatewayPath("notorious/port3", List("notorious", "port3")), None, None, Nil, Nil, None))))),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil, Map())), Nil, None, Map())))
    )
  }

  it should "fail on gateway weight > 100" in {
    expectedError[GatewayRouteWeightError]({
      BlueprintReader.read(res("blueprint/blueprint73.yml"))
    }) should have(
      'gateway(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", GatewayPath("notorious/port1", List("notorious", "port1")), Some(Percentage(50)), None, Nil, Nil, None), DefaultRoute("", GatewayPath("notorious/port2", List("notorious", "port2")), Some(Percentage(60)), None, Nil, Nil, None))))
    )
  }

  it should "fail on duplicate gateway port" in {
    expectedError[DuplicateGatewayPortError]({
      BlueprintReader.read(res("blueprint/blueprint74.yml"))
    }) should have(
      'port(8080)
    )
  }

  it should "fail on explicit routing port" in {
    expectedError[UnexpectedElement]({
      BlueprintReader.read(res("blueprint/blueprint75.yml"))
    }) should have(
      'element(Map("web" -> "port"))
    )
  }

  it should "read explicit gateway port" in {
    BlueprintReader.read(res("blueprint/blueprint76.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil, Map())), Nil, None, Map()))),
      'gateways(List(Gateway("", Port("web", None, Some("8080/http")), None, None, Nil, List(DefaultRoute("", GatewayPath("notorious/web", List("notorious", "web")), None, None, Nil, Nil, None)))))
    )
  }

  it should "not allow non existing cluster routing port" in {
    expectedError[UnresolvedGatewayPortError]({
      BlueprintReader.read(res("blueprint/blueprint77.yml"))
    }) should have(
      'name("port1"),
      'value("")
    )
  }

  it should "parse weights with filter conditions and strength" in {
    BlueprintReader.read(res("blueprint/blueprint78.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil, Map())), Nil, None, Map()))),
      'gateways(List(Gateway("", Port("8080", None, Some("8080")), None, None, Nil, List(DefaultRoute("", GatewayPath("notorious/port1", List("notorious", "port1")), Some(Percentage.of("90%")), None, Nil, Nil, None), DefaultRoute("", GatewayPath("notorious/port2", List("notorious", "port2")), Some(Percentage.of("10%")), Some(Percentage.of("10%")), List(DefaultCondition("", "user-agent = firefox")), Nil, None)))))
    )
  }

  it should "parse service arguments" in {
    BlueprintReader.read(res("blueprint/blueprint79.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, List(Argument("arg", "test"), Argument("privileged", "false")), Map())), Nil, None, Map())))
    )
  }

  it should "expand service arguments" in {
    BlueprintReader.read(res("blueprint/blueprint80.yml")) should have(
      'name("nomadic-frostbite"),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, List(Argument("arg1", "test1"), Argument("arg2", "test2")), Map())), Nil, None, Map())))
    )
  }

  it should "fail on invalid privileged value" in {
    expectedError[InvalidArgumentValueError]({
      BlueprintReader.read(res("blueprint/blueprint81.yml"))
    }) should have(
      'argument(Argument("privileged", "abcd"))
    )
  }

  it should "read virtual hosts" in {
    BlueprintReader.read(res("blueprint/blueprint82.yml")) should have(
      'name("nomadic-frostbite"),
      'gateways(List(Gateway("", Port("8080", None, Some("8080"), 8080, Port.Type.Http), None, None, List("test.com", "vamp"), List(DefaultRoute("", GatewayPath("notorious/port1", List("notorious", "port1")), Some(Percentage(100)), None, Nil, Nil, None, Nil))), Gateway("", Port("8081", None, Some("8081"), 8081, Port.Type.Http), None, None, List("test"), List(DefaultRoute("", GatewayPath("notorious/port1", List("notorious", "port1")), Some(Percentage(100)), None, Nil, Nil, None, Nil))))),
      'clusters(List(Cluster("notorious", List(Service(BreedReference("nocturnal-viper"), Nil, None, Nil, Map())), List(Gateway("", Port("web", None, None, 0, Port.Type.Http), None, None, List("route"), Nil)), None, Map())))
    )
  }
}
