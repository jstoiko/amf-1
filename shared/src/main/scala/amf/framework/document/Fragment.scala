package amf.framework.document

import amf.domain._
import amf.domain.extensions.CustomDomainProperty
import amf.framework.parser.Annotations
import amf.metadata.document.{BaseUnitModel, DocumentModel, FragmentModel}
import amf.model.AmfObject
import amf.shape.Shape

/**
  * RAML Fragments
  */
object Fragment {

  /** Units encoding domain fragments */
  trait Fragment extends BaseUnit with EncodesModel {

    /** Returns the list document URIs referenced from the document that has been parsed to generate this model */
    override val references: Seq[BaseUnit] = fields(DocumentModel.References)

    override def adopted(parent: String): this.type = withId(parent)

    override def usage: String = ""

    override def encodes: DomainElement = fields(FragmentModel.Encodes)

    override def location: String = fields(BaseUnitModel.Location)
  }

  // todo review

  case class DocumentationItem(fields: Fields, annotations: Annotations) extends Fragment {
    override def encodes: CreativeWork = super.encodes.asInstanceOf[CreativeWork]
  }

  case class DataType(fields: Fields, annotations: Annotations) extends Fragment {
    override def encodes: Shape = super.encodes.asInstanceOf[Shape]
  }

  case class NamedExample(fields: Fields, annotations: Annotations) extends Fragment {
    override def encodes: Example = super.encodes.asInstanceOf[Example]
  }

  case class ResourceTypeFragment(fields: Fields, annotations: Annotations) extends Fragment

  case class TraitFragment(fields: Fields, annotations: Annotations) extends Fragment

  case class AnnotationTypeDeclaration(fields: Fields, annotations: Annotations) extends Fragment {
    override def encodes: CustomDomainProperty = super.encodes.asInstanceOf[CustomDomainProperty]
  }

  case class SecurityScheme(fields: Fields, annotations: Annotations) extends Fragment {
    override def encodes: amf.domain.security.SecurityScheme =
      super.encodes.asInstanceOf[amf.domain.security.SecurityScheme]
  }

  case class ExternalFragment(fields: Fields, annotations: Annotations) extends Fragment {
    override def encodes: ExternalDomainElement = super.encodes.asInstanceOf[ExternalDomainElement]
  }

  case class DialectFragment(fields: Fields, annotations: Annotations) extends Fragment

  object DocumentationItem {
    def apply(): DocumentationItem = apply(Annotations())

    def apply(annotations: Annotations): DocumentationItem = apply(Fields(), annotations)
  }

  object DataType {
    def apply(): DataType = apply(Annotations())

    def apply(annotations: Annotations): DataType = apply(Fields(), annotations)
  }

  object NamedExample {
    def apply(): NamedExample = apply(Annotations())

    def apply(annotations: Annotations): NamedExample = apply(Fields(), annotations)
  }

  object ResourceTypeFragment {
    def apply(): ResourceTypeFragment = apply(Annotations())

    def apply(annotations: Annotations): ResourceTypeFragment = apply(Fields(), annotations)
  }

  object TraitFragment {
    def apply(): TraitFragment = apply(Annotations())

    def apply(annotations: Annotations): TraitFragment = apply(Fields(), annotations)
  }

  object AnnotationTypeDeclaration {
    def apply(): AnnotationTypeDeclaration = apply(Annotations())

    def apply(annotations: Annotations): AnnotationTypeDeclaration = apply(Fields(), annotations)
  }

  object DialectFragment {
    def apply(): DialectFragment = apply(Annotations())

    def apply(annotations: Annotations): DialectFragment = apply(Fields(), annotations)
  }

  object ExternalFragment {
    def apply(): ExternalFragment                         = apply(Annotations())
    def apply(annotations: Annotations): ExternalFragment = ExternalFragment(Fields(), annotations)
  }

  object SecurityScheme {
    def apply(): SecurityScheme = apply(Annotations())

    def apply(annotations: Annotations): SecurityScheme = apply(Fields(), annotations)
  }
}

trait EncodesModel extends AmfObject {

  /** Encoded [[amf.domain.DomainElement]] described in the document element. */
  def encodes: DomainElement

  def withEncodes(encoded: DomainElement): this.type = set(FragmentModel.Encodes, encoded)
}