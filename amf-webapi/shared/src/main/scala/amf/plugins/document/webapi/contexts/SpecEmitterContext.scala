package amf.plugins.document.webapi.contexts

import amf.core.annotations.DomainExtensionAnnotation
import amf.core.emitter.BaseEmitters._
import amf.core.emitter.SpecOrdering.Default
import amf.core.emitter._
import amf.core.metamodel.Field
import amf.core.model.document.{BaseUnit, DeclaresModel, Document, EncodesModel}
import amf.core.model.domain.extensions.{CustomDomainProperty, DomainExtension, ShapeExtension}
import amf.core.model.domain.{DomainElement, Linkable, Shape}
import amf.core.parser.{FieldEntry, Position}
import amf.core.remote._
import amf.core.emitter._
import amf.plugins.document.webapi.model.{Extension, Overlay}
import amf.plugins.document.webapi.parser.spec.declaration._
import amf.plugins.document.webapi.parser.spec.domain._
import amf.plugins.document.webapi.parser.spec.raml.{Raml08RootLevelEmitters, Raml10RootLevelEmitters, RamlRootLevelEmitters}
import amf.plugins.document.webapi.parser.{CommonOasTypeDefMatcher, JsonSchemaTypeDefMatcher, OasTypeDefStringValueMatcher, RamlHeader}
import amf.plugins.domain.shapes.metamodel.NodeShapeModel
import amf.plugins.domain.shapes.models.AnyShape
import amf.plugins.domain.webapi.annotations.TypePropertyLexicalInfo
import amf.plugins.domain.webapi.models._
import amf.plugins.domain.webapi.models.security.{ParametrizedSecurityScheme, SecurityScheme}
import org.yaml.model.YDocument.{EntryBuilder, PartBuilder}
import org.yaml.model.{YNode, YScalar, YType}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import amf.core.utils.Strings

/**
  *
  */
abstract class SpecEmitterContext(refEmitter: RefEmitter) {

  def ref(b: PartBuilder, url: String): Unit = refEmitter.ref(url, b)

  def localReference(reference: Linkable): PartEmitter

  val vendor: Vendor

  val factory: SpecEmitterFactory

  def getRefEmitter: RefEmitter = refEmitter

  def oasTypePropertyEmitter(typeName: String, shape: Shape): MapEntryEmitter = {
    shape.annotations.find(classOf[TypePropertyLexicalInfo]) match {
      case Some(lexicalInfo) =>
        MapEntryEmitter("type", typeName, YType.Str, lexicalInfo.range.start)
      case None =>
        MapEntryEmitter("type", typeName)
    }
  }

  def ramlTypePropertyEmitter(typeName: String, shape: Shape): Option[MapEntryEmitter] = {
    shape.fields.?(NodeShapeModel.Inherits) match {
      case None =>
        shape.annotations.find(classOf[TypePropertyLexicalInfo]) match {
          case Some(lexicalInfo) =>
            Some(MapEntryEmitter("type", typeName, YType.Str, lexicalInfo.range.start))
          case _ => None
        }
      case _ => None
    }
  }

  private var emittingDeclarations: Boolean = false

  def runAsDeclarations(fn: () => Unit): Unit = {
    emittingDeclarations = true
    fn()
    emittingDeclarations = false
  }

  def filterLocal[T <: DomainElement](elements: Seq[T]): Seq[T] = {
    if (!emittingDeclarations) elements
    else elements.filter(!_.fromLocal())
  }

  def externalLink(link: Linkable, refs: Seq[BaseUnit]): Option[BaseUnit] = {
    link.linkTarget match {
      case Some(element) =>
        val linkTarget = element.id
        refs.find {
          case fragment: EncodesModel =>
            fragment.encodes.id == linkTarget
          case library: DeclaresModel =>
            library.declares.exists(_.id == linkTarget)
          case _ => false
        }

      case _ => None
    }
  }
}

trait SpecEmitterFactory {
  def tagToReferenceEmitter: (DomainElement, Option[String], Seq[BaseUnit]) => TagToReferenceEmitter

  def customFacetsEmitter: (FieldEntry, SpecOrdering, Seq[BaseUnit]) => CustomFacetsEmitter

  def facetsInstanceEmitter: (ShapeExtension, SpecOrdering) => FacetsInstanceEmitter

  def annotationEmitter: (DomainExtension, SpecOrdering) => AnnotationEmitter

  def parametrizedSecurityEmitter: (ParametrizedSecurityScheme, SpecOrdering) => ParametrizedSecuritySchemeEmitter

  def annotationTypeEmitter: (CustomDomainProperty, SpecOrdering) => AnnotationTypeEmitter

  def headerEmitter: (Parameter, SpecOrdering, Seq[BaseUnit]) => EntryEmitter

  def declaredTypesEmitter: (Seq[Shape], Seq[BaseUnit], SpecOrdering) => EntryEmitter
}

trait TagToReferenceEmitter extends PartEmitter {
  val target: DomainElement

  val label: Option[String]

  val referenceLabel: String = label.getOrElse(target.id)
}

trait BaseSpecEmitter {
  implicit val spec: SpecEmitterContext
}

/** Scalar valued raml node (emit as obj node). */
private case class RamlScalarValueEmitter(key: String,
                                          f: FieldEntry,
                                          extensions: Seq[DomainExtension],
                                          mediaType: Option[YType] = None)(implicit spec: SpecEmitterContext)
    extends BaseValueEmitter {

  override def emit(b: EntryBuilder): Unit = sourceOr(f.value, annotatedScalar(b))

  private def annotatedScalar(b: EntryBuilder): Unit = {
    b.entry(
      key,
      _.obj { b =>
        b.value = YNode(YScalar(f.scalar.value), mediaType.getOrElse(tag))
        extensions.foreach { e =>
          spec.factory.annotationEmitter(e, Default).emit(b)
        }
      }
    )
  }
}

object RamlScalarEmitter {
  def apply(key: String, f: FieldEntry, mediaType: Option[YType] = None)(
      implicit spec: SpecEmitterContext): EntryEmitter = {
    val extensions = f.value.value.annotations.collect({ case e: DomainExtensionAnnotation => e })
    if (extensions.nonEmpty && spec.vendor == Raml10) {
      RamlScalarValueEmitter(key, f, extensions.map(_.extension), mediaType)
    } else {
      ValueEmitter(key, f, mediaType)
    }
  }
}

abstract class OasSpecEmitterFactory(implicit val spec: OasSpecEmitterContext) extends SpecEmitterFactory {
  override def tagToReferenceEmitter: (DomainElement, Option[String], Seq[BaseUnit]) => TagToReferenceEmitter =
    OasTagToReferenceEmitter.apply

  override def customFacetsEmitter: (FieldEntry, SpecOrdering, Seq[BaseUnit]) => CustomFacetsEmitter =
    OasCustomFacetsEmitter.apply

  override def facetsInstanceEmitter: (ShapeExtension, SpecOrdering) => FacetsInstanceEmitter =
    OasFacetsInstanceEmitter.apply

  override def annotationEmitter: (DomainExtension, SpecOrdering) => AnnotationEmitter = OasAnnotationEmitter.apply

  override def parametrizedSecurityEmitter
    : (ParametrizedSecurityScheme, SpecOrdering) => ParametrizedSecuritySchemeEmitter =
    OasParametrizedSecuritySchemeEmitter.apply

  override def annotationTypeEmitter: (CustomDomainProperty, SpecOrdering) => AnnotationTypeEmitter =
    OasAnnotationTypeEmitter.apply

  def serversEmitter(api: WebApi, f: FieldEntry, ordering: SpecOrdering, references: Seq[BaseUnit]): OasServersEmitter

  def headerEmitter: (Parameter, SpecOrdering, Seq[BaseUnit]) => EntryEmitter = OasHeaderEmitter.apply

  override def declaredTypesEmitter: (Seq[Shape], Seq[BaseUnit], SpecOrdering) => EntryEmitter =
    OasDeclaredTypesEmitters.apply
}

case class Oas2SpecEmitterFactory(implicit override val spec: OasSpecEmitterContext) extends OasSpecEmitterFactory {
  override def serversEmitter(api: WebApi, f: FieldEntry, ordering: SpecOrdering, references: Seq[BaseUnit]) =
    Oas2ServersEmitter(api, f, ordering, references)
}

case class Oas3SpecEmitterFactory(implicit override val spec: OasSpecEmitterContext) extends OasSpecEmitterFactory {
  override def serversEmitter(api: WebApi, f: FieldEntry, ordering: SpecOrdering, references: Seq[BaseUnit]) =
    Oas3ServersEmitter(api, f, ordering, references)
}

trait RamlEmitterVersionFactory extends SpecEmitterFactory {

  implicit val spec: RamlSpecEmitterContext

  def retrieveHeader(document: BaseUnit): String

  def endpointEmitter
    : (EndPoint, SpecOrdering, mutable.ListBuffer[RamlEndPointEmitter], Seq[BaseUnit]) => RamlEndPointEmitter

  def parameterEmitter: (Parameter, SpecOrdering, Seq[BaseUnit]) => RamlParameterEmitter

  def headerEmitter: (Parameter, SpecOrdering, Seq[BaseUnit]) => EntryEmitter = parameterEmitter

  val typesKey: String

  def typesEmitter
    : (AnyShape, SpecOrdering, Option[AnnotationsEmitter], Seq[Field], Seq[BaseUnit]) => RamlTypePartEmitter

  def namedSecurityEmitter: (SecurityScheme, Seq[BaseUnit], SpecOrdering) => RamlNamedSecuritySchemeEmitter

  def rootLevelEmitters: (BaseUnit with DeclaresModel, SpecOrdering) => RamlRootLevelEmitters

  override def tagToReferenceEmitter: (DomainElement, Option[String], Seq[BaseUnit]) => TagToReferenceEmitter =
    RamlTagToReferenceEmitter.apply

  override def parametrizedSecurityEmitter
    : (ParametrizedSecurityScheme, SpecOrdering) => ParametrizedSecuritySchemeEmitter =
    RamlParametrizedSecuritySchemeEmitter.apply

  def payloadsEmitter: (String, FieldEntry, SpecOrdering, Seq[BaseUnit]) => RamlPayloadsEmitter

  def responseEmitter: (Response, SpecOrdering, Seq[BaseUnit]) => RamlResponseEmitter

  def operationEmitter: (Operation, SpecOrdering, Seq[BaseUnit]) => RamlOperationEmitter

  override def declaredTypesEmitter: (Seq[Shape], Seq[BaseUnit], SpecOrdering) => EntryEmitter =
    RamlDeclaredTypesEmitters.apply
}

class Raml10EmitterVersionFactory()(implicit val spec: RamlSpecEmitterContext) extends RamlEmitterVersionFactory {
  override def retrieveHeader(document: BaseUnit): String = document match {
    case _: Extension => RamlHeader.Raml10Extension.text
    case _: Overlay   => RamlHeader.Raml10Overlay.text
    case _: Document  => RamlHeader.Raml10.text
    case _            => throw new Exception("Document has no header.")
  }

  override def endpointEmitter
    : (EndPoint, SpecOrdering, ListBuffer[RamlEndPointEmitter], Seq[BaseUnit]) => RamlEndPointEmitter =
    Raml10EndPointEmitter.apply

  override val typesKey: String = "types"

  override def typesEmitter
    : (AnyShape, SpecOrdering, Option[AnnotationsEmitter], Seq[Field], Seq[BaseUnit]) => RamlTypePartEmitter =
    Raml10TypePartEmitter.apply

  override def namedSecurityEmitter: (SecurityScheme, Seq[BaseUnit], SpecOrdering) => RamlNamedSecuritySchemeEmitter =
    Raml10NamedSecuritySchemeEmitter.apply

  override def rootLevelEmitters: (BaseUnit with DeclaresModel, SpecOrdering) => RamlRootLevelEmitters =
    Raml10RootLevelEmitters.apply

  override def customFacetsEmitter: (FieldEntry, SpecOrdering, Seq[BaseUnit]) => CustomFacetsEmitter =
    RamlCustomFacetsEmitter.apply

  override def facetsInstanceEmitter: (ShapeExtension, SpecOrdering) => FacetsInstanceEmitter =
    RamlFacetsInstanceEmitter.apply

  override def annotationEmitter: (DomainExtension, SpecOrdering) => AnnotationEmitter =
    RamlAnnotationEmitter.apply

  override def annotationTypeEmitter: (CustomDomainProperty, SpecOrdering) => AnnotationTypeEmitter =
    RamlAnnotationTypeEmitter.apply

  override def parameterEmitter: (Parameter, SpecOrdering, Seq[BaseUnit]) => RamlParameterEmitter =
    Raml10ParameterEmitter.apply

  override def payloadsEmitter: (String, FieldEntry, SpecOrdering, Seq[BaseUnit]) => RamlPayloadsEmitter =
    Raml10PayloadsEmitter.apply

  override def operationEmitter: (Operation, SpecOrdering, Seq[BaseUnit]) => RamlOperationEmitter =
    Raml10OperationEmitter.apply

  override def responseEmitter: (Response, SpecOrdering, Seq[BaseUnit]) => RamlResponseEmitter =
    Raml10ResponseEmitter.apply
}

class Raml08EmitterVersionFactory()(implicit val spec: RamlSpecEmitterContext) extends RamlEmitterVersionFactory {
  override def retrieveHeader(document: BaseUnit): String = document match {
    case _: Document => RamlHeader.Raml08.text
    case _           => throw new Exception("Document has no header.")
  }

  override def endpointEmitter
    : (EndPoint, SpecOrdering, ListBuffer[RamlEndPointEmitter], Seq[BaseUnit]) => RamlEndPointEmitter =
    Raml08EndPointEmitter.apply

  override val typesKey: String = "schemas"

  override def typesEmitter
    : (AnyShape, SpecOrdering, Option[AnnotationsEmitter], Seq[Field], Seq[BaseUnit]) => RamlTypePartEmitter =
    Raml08TypePartEmitter.apply

  override def namedSecurityEmitter: (SecurityScheme, Seq[BaseUnit], SpecOrdering) => RamlNamedSecuritySchemeEmitter =
    Raml08NamedSecuritySchemeEmitter.apply

  override def rootLevelEmitters: (BaseUnit with DeclaresModel, SpecOrdering) => RamlRootLevelEmitters =
    Raml08RootLevelEmitters.apply

  override def customFacetsEmitter: (FieldEntry, SpecOrdering, Seq[BaseUnit]) => CustomFacetsEmitter =
    throw new Exception(s"Custom facets not supported for vendor ${spec.vendor}")

  override def facetsInstanceEmitter: (ShapeExtension, SpecOrdering) => FacetsInstanceEmitter =
    throw new Exception(s"Facerts not supported for vendor ${spec.vendor}")

  override def annotationEmitter: (DomainExtension, SpecOrdering) => AnnotationEmitter =
    throw new Exception(s"Annotations not supported for vendor ${spec.vendor}")

  override def annotationTypeEmitter: (CustomDomainProperty, SpecOrdering) => AnnotationTypeEmitter =
    throw new Exception(s"Annotation types not supported for vendor ${spec.vendor}")

  override def parameterEmitter: (Parameter, SpecOrdering, Seq[BaseUnit]) => RamlParameterEmitter =
    Raml08ParameterEmitter.apply

  override def payloadsEmitter: (String, FieldEntry, SpecOrdering, Seq[BaseUnit]) => RamlPayloadsEmitter =
    Raml08PayloadsEmitter.apply

  override def operationEmitter: (Operation, SpecOrdering, Seq[BaseUnit]) => RamlOperationEmitter =
    Raml08OperationEmitter.apply

  override def responseEmitter: (Response, SpecOrdering, Seq[BaseUnit]) => RamlResponseEmitter =
    Raml08ResponseEmitter.apply

}

class Raml10SpecEmitterContext(refEmitter: RefEmitter = RamlRefEmitter) extends RamlSpecEmitterContext(refEmitter) {
  override val factory: RamlEmitterVersionFactory = new Raml10EmitterVersionFactory()(this)
  override val vendor: Vendor                     = Raml10
}

class XRaml10SpecEmitterContext(refEmitter: RefEmitter = OasRefEmitter) extends Raml10SpecEmitterContext(refEmitter) {
  override def localReference(reference: Linkable): PartEmitter =
    oasFactory.tagToReferenceEmitter(reference.asInstanceOf[DomainElement], reference.linkLabel.option(), Nil)

  val oasFactory: OasSpecEmitterFactory = Oas2SpecEmitterFactory()(new Oas2SpecEmitterContext(refEmitter))
}

class Raml08SpecEmitterContext extends RamlSpecEmitterContext(RamlRefEmitter) {
  override val factory: RamlEmitterVersionFactory = new Raml08EmitterVersionFactory()(this)
  override val vendor: Vendor                     = Raml08

}

abstract class RamlSpecEmitterContext(refEmitter: RefEmitter) extends SpecEmitterContext(refEmitter) {

  import BaseEmitters._

  override def localReference(reference: Linkable): PartEmitter = RamlLocalReferenceEmitter(reference)

  def externalReference(location: String, reference: Linkable): PartEmitter =
    new PartEmitter {
      override def emit(b: PartBuilder): Unit = b += YNode.include(reference.linkLabel.option().getOrElse(reference.location().get))
      override def position(): Position           = pos(reference.annotations)
    }

  val factory: RamlEmitterVersionFactory

}

abstract class OasSpecEmitterContext(refEmitter: RefEmitter = OasRefEmitter) extends SpecEmitterContext(refEmitter) {
  override def localReference(reference: Linkable): PartEmitter =
    factory.tagToReferenceEmitter(reference.asInstanceOf[DomainElement], reference.linkLabel.option(), Nil)

  val factory: OasSpecEmitterFactory
  val jsonPointersMap: mutable.Map[String, String] = mutable.Map() // id -> pointer

  val typeDefMatcher: OasTypeDefStringValueMatcher = CommonOasTypeDefMatcher

  val anyOfKey: String = "union".asOasExtension
}

final case class JsonSchemaEmitterContext() extends Oas2SpecEmitterContext {
  override val typeDefMatcher: OasTypeDefStringValueMatcher = JsonSchemaTypeDefMatcher

  override val anyOfKey: String = "anyOf"
}

class Oas3SpecEmitterContext(refEmitter: RefEmitter = OasRefEmitter) extends OasSpecEmitterContext(refEmitter) {
  override val factory: OasSpecEmitterFactory = Oas3SpecEmitterFactory()(this)
  override val vendor: Vendor                 = Oas3
}

class Oas2SpecEmitterContext(refEmitter: RefEmitter = OasRefEmitter) extends OasSpecEmitterContext(refEmitter) {
  override val factory: OasSpecEmitterFactory = Oas2SpecEmitterFactory()(this)
  override val vendor: Vendor                 = Oas2
}

trait RefEmitter {
  def ref(url: String, b: PartBuilder): Unit
}

object OasRefEmitter extends RefEmitter {

  override def ref(url: String, b: PartBuilder): Unit = b.obj(MapEntryEmitter("$ref", url).emit(_))
}

object RamlRefEmitter extends RefEmitter {
  override def ref(url: String, b: PartBuilder): Unit = b += YNode.include(url)
}
