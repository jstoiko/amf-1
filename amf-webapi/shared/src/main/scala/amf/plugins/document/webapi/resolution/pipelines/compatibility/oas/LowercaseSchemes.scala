package amf.plugins.document.webapi.resolution.pipelines.compatibility.oas

import amf.core.errorhandling.ErrorHandler
import amf.core.metamodel.Field
import amf.core.model.StrField
import amf.core.model.document.{BaseUnit, Document}
import amf.core.model.domain.DomainElement
import amf.core.resolution.stages.ResolutionStage
import amf.plugins.domain.webapi.metamodel.{OperationModel, WebApiModel}
import amf.plugins.domain.webapi.models.{Operation, WebApi}

import scala.language.postfixOps

class LowercaseSchemes()(override implicit val errorHandler: ErrorHandler) extends ResolutionStage {

  private def capitalizeProtocols(element: DomainElement, schemes: Seq[StrField], field: Field) = {
    val valid = Seq("HTTP", "HTTPS")
    val s =
      schemes.flatMap(_.option()).filter(scheme => valid.exists(scheme.equalsIgnoreCase)).map(_.toLowerCase)
    element.fields.removeField(field)
    if (s.nonEmpty) element.set(field, s)
  }

  override def resolve[T <: BaseUnit](model: T): T = model match {
    case d: Document if d.encodes.isInstanceOf[WebApi] =>
      try {
        val api = d.encodes.asInstanceOf[WebApi]
        capitalizeProtocols(api, api.schemes, WebApiModel.Schemes)

        model.iterator().foreach {
          case op: Operation =>
            capitalizeProtocols(op, op.schemes, OperationModel.Schemes)
          case _ => // ignore
        }
      } catch {
        case _: Throwable => // ignore: we don't want this to break anything
      }
      model
    case _ => model
  }
}
