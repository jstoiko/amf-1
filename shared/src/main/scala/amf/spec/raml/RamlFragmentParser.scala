package amf.spec.raml

import amf.compiler.RamlFragmentHeader._
import amf.compiler.{RamlFragmentHeader, Root}
import amf.document.Fragment._
import amf.domain.Annotations
import amf.domain.`abstract`.{ResourceType, Trait}
import amf.domain.extensions.CustomDomainProperty
import amf.parser.YValueOps
import amf.shape.Shape
import amf.spec.Declarations
import amf.spec.common.BaseSpecParser.AbstractDeclarationParser
import org.yaml.model.YMap

/**
  *
  */
case class RamlFragmentParser(override val root: Root) extends RamlSpecParser(root) {

  def parseFragment(): Fragment = {
    // first i must identify the type of fragment

    val rootMap: YMap =
      root.document.value.map(_.toMap).getOrElse(throw new RuntimeException("Cannot parse empty map"))

    val fragment: Fragment = RamlFragmentHeader(root) match {
      case Some(Raml10DocumentationItem)         => DocumentationItemFragmentParser(rootMap).parse()
      case Some(Raml10DataType)                  => DataTypeFragmentParser(rootMap).parse()
      case Some(Raml10NamedExample)              => throw new IllegalStateException("to be implemented")
      case Some(Raml10ResourceType)              => ResourceTypeFragmentParser(rootMap).parse()
      case Some(Raml10Trait)                     => TraitFragmentParser(rootMap).parse()
      case Some(Raml10AnnotationTypeDeclaration) => AnnotationFragmentParser(rootMap).parse()
      case _                                     => throw new IllegalStateException("Unsupported fragment type")
    }

    UsageParser(rootMap, fragment).parse()

    fragment.add(Annotations(root.document))

    val environmentRef = ModulesParser(rootMap, root.references).parse()

    if (environmentRef.nonEmpty)
      fragment.withReferences(environmentRef.values.toSeq)
    fragment
  }

  case class DocumentationItemFragmentParser(map: YMap) {
    def parse(): DocumentationItem = {

      val item = DocumentationItem().adopted(root.location)

      item.withEncodes(UserDocumentationParser(map).parse())

      item
    }
  }

  case class DataTypeFragmentParser(map: YMap) {
    def parse(): DataType = {
      val dataType = DataType().adopted(root.location)

      val shapeOption =
        RamlTypeParser(map, "type", map, (shape: Shape) => shape.adopted(root.location), Declarations()).parse()
      shapeOption.map(dataType.withEncodes(_))

      dataType
      //
    }
  }

  case class ResourceTypeFragmentParser(map: YMap) {
    def parse(): ResourceTypeFragment = {
      val resourceType = ResourceTypeFragment().adopted(root.location)

      val abstractDeclaration =
        new AbstractDeclarationParser(ResourceType(map), resourceType.id, "resourceType", map, Declarations()).parse()

      resourceType.withEncodes(abstractDeclaration)

    }
  }

  case class TraitFragmentParser(map: YMap) {
    def parse(): TraitFragment = {
      val traitFragment = TraitFragment().adopted(root.location)

      val abstractDeclaration =
        new AbstractDeclarationParser(Trait(map), traitFragment.id, "trait", map, Declarations()).parse()

      traitFragment.withEncodes(abstractDeclaration)
    }
  }

  case class AnnotationFragmentParser(map: YMap) {
    def parse(): AnnotationTypeDeclaration = {
      val annotation = AnnotationTypeDeclaration().adopted(root.location)

      val property = AnnotationTypesParser(map,
                                           "annotation",
                                           map,
                                           (annotation: CustomDomainProperty) => annotation.adopted(root.location),
                                           Declarations()).parse()

      annotation.withEncodes(property)
    }
  }
}
