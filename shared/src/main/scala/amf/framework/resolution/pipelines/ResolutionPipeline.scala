package amf.framework.resolution.pipelines

import amf.ProfileNames
import amf.framework.model.document.BaseUnit
import amf.framework.resolution.stages.ResolutionStage
import amf.plugins.document.graph.resolution.pipelines.AmfResolutionPipeline
import amf.plugins.document.webapi.resolution.pipelines.{OasResolutionPipeline, RamlResolutionPipeline}

abstract class ResolutionPipeline {

  var model: Option[BaseUnit] = None

  def resolve[T <: BaseUnit](model: T): T

  protected def step(stage: ResolutionStage): Unit = {
    model = Some(stage.resolve(model.get))
  }

  protected def withModel[T <: BaseUnit](unit: T)(block: () => Unit): T = {
    model = Some(unit)
    block()
    model.get.asInstanceOf[T]
  }
}

object ResolutionPipeline {

  def raml = new RamlResolutionPipeline()
  def oas  = new OasResolutionPipeline()
  def amf  = new AmfResolutionPipeline()

  def forProfile(profile: String): ResolutionPipeline = {
    profile match {
      case ProfileNames.RAML => raml
      case ProfileNames.OAS  => oas
      case ProfileNames.AMF  => amf
      case _                 => throw new Exception(s"Unknown resolution pipeline $profile")
    }
  }
}