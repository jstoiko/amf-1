package amf.framework.model.document

import amf.domain._
import amf.framework.metamodel.Obj
import amf.framework.metamodel.document.DocumentModel
import amf.framework.parser.Annotations
import amf.framework.metamodel.document.DocumentModel._
import amf.framework.model.domain.DomainElement
import amf.plugins.document.webapi.metamodel.model.{ExtensionLikeModel, ExtensionModel, OverlayModel}

/**
  * A [[Document]] is a parsing Unit that encodes a stand-alone [[DomainElement]] and can include references to other
  * [[DomainElement]]s that reference from the encoded [[DomainElement]]
  */
case class Document(fields: Fields, annotations: Annotations) extends BaseUnit with EncodesModel with DeclaresModel {

  override def references: Seq[BaseUnit] = fields(References)

  override def location: String = fields(Location)

  override def encodes: DomainElement = fields(Encodes)

  override def declares: Seq[DomainElement] = fields(Declares)

  /** Returns the usage comment for de element */
  override def usage: String = fields(Usage)

  override def adopted(parent: String): this.type = withId(parent)

  /** Meta data for the document */
  override def meta: Obj = DocumentModel
}

object Document {
  def apply(): Document = apply(Annotations())

  def apply(annotations: Annotations): Document = Document(Fields(), annotations)
}

abstract class ExtensionLike(override val fields: Fields, override val annotations: Annotations)
    extends Document(fields, annotations) {
  override def encodes: WebApi = super.encodes.asInstanceOf[WebApi]
  def extend: String           = fields(ExtensionLikeModel.Extends)

  def withExtend(extend: BaseUnit): this.type = set(ExtensionLikeModel.Extends, extend)
}

class Overlay(override val fields: Fields, override val annotations: Annotations)
    extends ExtensionLike(fields, annotations) {
  override def meta = OverlayModel
}

object Overlay {
  def apply(): Overlay = apply(Annotations())

  def apply(annotations: Annotations): Overlay = new Overlay(Fields(), annotations)

}

class Extension(override val fields: Fields, override val annotations: Annotations)
    extends ExtensionLike(fields, annotations) {
  override def meta = ExtensionModel
}

object Extension {
  def apply(): Extension = apply(Annotations())

  def apply(annotations: Annotations): Extension = new Extension(Fields(), annotations)

}