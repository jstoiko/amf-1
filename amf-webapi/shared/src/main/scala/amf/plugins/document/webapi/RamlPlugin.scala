package amf.plugins.document.webapi

import amf.{ProfileName, Raml08Profile, RamlProfile}
import amf.core.emitter.RenderOptions
import amf.core.Root
import amf.core.client.ParsingOptions
import amf.core.model.document._
import amf.core.model.domain.{DomainElement, ExternalDomainElement}
import amf.core.parser.{EmptyFutureDeclarations, LinkReference, ParsedDocument, ParserContext, RefContainer, SyamlParsedDocument}
import amf.core.remote.{Platform, Raml, Vendor}
import amf.core.resolution.pipelines.ResolutionPipeline
import amf.plugins.document.webapi.contexts._
import amf.plugins.document.webapi.model._
import amf.plugins.document.webapi.parser.RamlFragmentHeader._
import amf.plugins.document.webapi.parser.RamlHeader.{Raml10, Raml10Extension, Raml10Library, Raml10Overlay, _}
import amf.plugins.document.webapi.parser.spec.{RamlWebApiDeclarations, WebApiDeclarations}
import amf.plugins.document.webapi.parser.spec.raml.{RamlDocumentEmitter, RamlFragmentEmitter, RamlModuleEmitter, _}
import amf.plugins.document.webapi.parser.{RamlFragment, RamlHeader}
import amf.plugins.document.webapi.resolution.pipelines.{Raml08EditingPipeline, Raml08ResolutionPipeline, Raml10EditingPipeline, Raml10ResolutionPipeline}
import amf.plugins.domain.webapi.models.WebApi
import org.yaml.model.YNode.MutRef
import org.yaml.model.{YDocument, YNode}

sealed trait RamlPlugin extends BaseWebApiPlugin {

  override val vendors = Seq(vendor.name, Raml.name)

  def context(wrapped: ParserContext, root: Root, ds: Option[WebApiDeclarations] = None): RamlWebApiContext

  // context that opens a new context for declarations and copies the global JSON Schema declarations
  def cleanContext(wrapped: ParserContext, root: Root) = {
    val cleanNested =
      ParserContext(root.location, root.references, EmptyFutureDeclarations(), parserCount = wrapped.parserCount)
    val clean = context(cleanNested, root)
    clean.globalSpace = wrapped.globalSpace
    clean
  }

  override def specContext: RamlSpecEmitterContext

  override def parse(root: Root,
                     parentContext: ParserContext,
                     platform: Platform,
                     options: ParsingOptions): Option[BaseUnit] = {

    inlineExternalReferences(root)

    val updated = context(parentContext, root)
    val clean   = cleanContext(parentContext, root)

    RamlHeader(root) flatMap { // todo review this, should we use the raml web api context for get the version parser?

      // Partial raml0.8 fragment with RAML header but linked through !include
      // we need to generate an external fragment and inline it in the parent document
      case Raml08 if root.referenceKind == LinkReference => None

      case Raml08          => Some(Raml08DocumentParser(root)(updated).parseDocument())
      case Raml10          => Some(Raml10DocumentParser(root)(updated).parseDocument())
      case Raml10Overlay   => Some(ExtensionLikeParser.apply(root, updated).parseOverlay())
      case Raml10Extension => Some(ExtensionLikeParser.apply(root, updated).parseExtension())
      case Raml10Library   => Some(RamlModuleParser(root)(clean).parseModule())
      case f: RamlFragment => RamlFragmentParser(root, f)(updated).parseFragment()
      case _               => None
    }
  }

  private def inlineExternalReferences(root: Root): Unit = {
    root.references.foreach { ref =>
      ref.unit match {
        case e: ExternalFragment => inlineFragment(ref.origin.refs, ref.ast, e.encodes)
        case _                   =>
      }
    }
  }

  private def inlineFragment(origins: Seq[RefContainer],
                             document: Option[YNode],
                             encodes: ExternalDomainElement): Unit = {
    origins.foreach { refContainer =>
      refContainer.node match {
        case mut: MutRef =>
          document match {
            case None => mut.target = Some(YNode(encodes.raw.value()))
            case _    => mut.target = document
          }
        case _ =>
      }
    }
  }

  /**
    * List of media types used to encode serialisations of
    * this domain
    */
  override def documentSyntaxes = Seq(
    "application/raml",
    "application/raml+json",
    "application/raml+yaml",
    "text/yaml",
    "text/x-yaml",
    "application/yaml",
    "application/x-yaml",
    "text/vnd.yaml"
  )
}

object Raml08Plugin extends RamlPlugin {

  override protected def vendor: Vendor = amf.core.remote.Raml08

  override val validationProfile: ProfileName = Raml08Profile

  def canParse(root: Root): Boolean = {
    RamlHeader(root) exists {
      case Raml08          => true
      case _: RamlFragment => true
      case _               => false
    }
  }

  // fix for 08
  override def canUnparse(unit: BaseUnit): Boolean = unit match {
    case _: Overlay                           => false
    case _: Extension                         => false
    case document: Document                   => document.encodes.isInstanceOf[WebApi]
    case _: Module                            => false
    case _: DocumentationItemFragment         => true // remove raml header and write as external fragment
    case _: DataTypeFragment                  => true
    case _: NamedExampleFragment              => true
    case _: ResourceTypeFragment              => true
    case _: TraitFragment                     => true
    case _: AnnotationTypeDeclarationFragment => true
    case _: SecuritySchemeFragment            => true
    case _: ExternalFragment                  => true
    case _                                    => false
  }

  // fix for 08?
  override def unparse(unit: BaseUnit, options: RenderOptions): Option[ParsedDocument] = {
    val unparsed = unit match {
      case document: Document => Some(RamlDocumentEmitter(document)(specContext).emitDocument())
      case fragment: Fragment => Some(new RamlFragmentEmitter(fragment)(specContext).emitFragment())
      case _                  => None
    }
    unparsed map { doc =>
      SyamlParsedDocument(document = doc)
    }
  }

  override def context(wrapped: ParserContext, root: Root, ds: Option[WebApiDeclarations] = None): RamlWebApiContext =
    new Raml08WebApiContext(root.location, root.references, wrapped, ds.map(d => RamlWebApiDeclarations(d)))

  def specContext: RamlSpecEmitterContext = new Raml08SpecEmitterContext

  /**
    * Resolves the provided base unit model, according to the semantics of the domain of the document
    */
  override def resolve(unit: BaseUnit, pipelineId: String = "default"): BaseUnit = {
    pipelineId match {
      case ResolutionPipeline.DEFAULT_PIPELINE => new Raml08ResolutionPipeline(unit).resolve()
      case ResolutionPipeline.EDITING_PIPELINE => new Raml08EditingPipeline(unit).resolve()
    }
  }
}

object Raml10Plugin extends RamlPlugin {


  override protected def vendor: Vendor = amf.core.remote.Raml10

  override val validationProfile: ProfileName = RamlProfile

  def canParse(root: Root): Boolean = RamlHeader(root) exists {
    case Raml10 | Raml10Overlay | Raml10Extension | Raml10Library => true
    case Raml10DocumentationItem | Raml10NamedExample | Raml10DataType | Raml10ResourceType | Raml10Trait |
        Raml10AnnotationTypeDeclaration | Raml10SecurityScheme =>
      true
    case _ => false
  }

  override def canUnparse(unit: BaseUnit): Boolean = unit match {
    case _: Overlay         => true
    case _: Extension       => true
    case document: Document => document.encodes.isInstanceOf[WebApi]
    case module: Module =>
      module.declares exists {
        case _: DomainElement => true
        case _                => false
      }
    case _: DocumentationItemFragment         => true
    case _: DataTypeFragment                  => true
    case _: NamedExampleFragment              => true
    case _: ResourceTypeFragment              => true
    case _: TraitFragment                     => true
    case _: AnnotationTypeDeclarationFragment => true
    case _: SecuritySchemeFragment            => true
    case _: ExternalFragment                  => true
    case _                                    => false
  }

  // fix for 08?
  override def unparse(unit: BaseUnit, options: RenderOptions): Option[ParsedDocument] = {
    val unparsed = unit match {
      case module: Module             => Some(RamlModuleEmitter(module)(specContext).emitModule())
      case document: Document         => Some(RamlDocumentEmitter(document)(specContext).emitDocument())
      case external: ExternalFragment => Some(YDocument(YNode(external.encodes.raw.value())))
      case fragment: Fragment         => Some(new RamlFragmentEmitter(fragment)(specContext).emitFragment())
      case _                          => None
    }
    unparsed map { doc =>
      SyamlParsedDocument(document = doc)
    }
  }

  override def context(wrapped: ParserContext, root: Root, ds: Option[WebApiDeclarations] = None): RamlWebApiContext =
    new Raml10WebApiContext(root.location, root.references, wrapped, ds.map(d => RamlWebApiDeclarations(d)))

  def specContext: RamlSpecEmitterContext = new Raml10SpecEmitterContext

  /**
    * Resolves the provided base unit model, according to the semantics of the domain of the document
    */
  override def resolve(unit: BaseUnit, pipelineId: String = ResolutionPipeline.DEFAULT_PIPELINE): BaseUnit = {
    pipelineId match {
      case ResolutionPipeline.DEFAULT_PIPELINE => new Raml10ResolutionPipeline(unit).resolve()
      case ResolutionPipeline.EDITING_PIPELINE => new Raml10EditingPipeline(unit).resolve()
    }
  }
}