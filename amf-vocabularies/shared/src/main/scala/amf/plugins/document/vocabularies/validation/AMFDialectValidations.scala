package amf.plugins.document.vocabularies.validation

import amf.core.validation.core.{PropertyConstraint, ValidationProfile, ValidationSpecification}
import amf.core.vocabulary.Namespace
import amf.plugins.document.vocabularies.emitters.instances.DialectEmitterHelper
import amf.plugins.document.vocabularies.model.document.Dialect
import amf.plugins.document.vocabularies.model.domain.{NodeMapping, PropertyMapping}
import amf.plugins.features.validation.ParserSideValidations
import org.yaml.model.YDocument.EntryBuilder

import scala.collection.mutable.ListBuffer

class AMFDialectValidations(val dialect: Dialect) extends DialectEmitterHelper {

  def profile() = {
    val parsedValidations = validations()
    ValidationProfile(
      name = dialect.nameAndVersion(),
      baseProfileName = None,
      violationLevel = parsedValidations.map(_.name),
      validations = parsedValidations ++ ParserSideValidations.validations
    )
  }

  protected def validations(): List[ValidationSpecification] = {
    Option(dialect.documents()).flatMap(docs => Option(docs.root())).flatMap(root => root.encoded().option()).map {
      mappingId =>
        Option(findNodeMappingById(mappingId)) match {
          case Some((_, nodeMapping)) => emitEntityValidations(nodeMapping)
          case _                      => Nil
        }

    } getOrElse Nil
  }

  protected def emitEntityValidations(node: NodeMapping): List[ValidationSpecification] = {
    node
      .propertiesMapping()
      .flatMap { propertyMapping =>
        emitPropertyValidations(node, propertyMapping)
      }
      .toList
  }

  protected def emitPropertyValidations(node: NodeMapping, prop: PropertyMapping): List[ValidationSpecification] = {
    val validations: ListBuffer[ValidationSpecification] = ListBuffer.empty

    prop.minimum().option().foreach { minValue =>
      val message = s"Property '${prop.name()}' minimum inclusive value is $minValue"
      validations += new ValidationSpecification(
        name = validationId(node, prop.name().value(), "minimum"),
        message = message,
        ramlMessage = Some(message),
        oasMessage = Some(message),
        targetClass = Seq(node.id),
        propertyConstraints = Seq(PropertyConstraint(
          ramlPropertyId = prop.nodePropertyMapping().value(),
          name = validationId(node, prop.name().value(), "minimum") + "/prop",
          message = Some(message),
          minInclusive = Some(minValue.toString)
        ))
      )
    }

    prop.maximum().option().foreach { maxValue =>
      val message = s"Property '${prop.name()}' maximum inclusive value is $maxValue"
      validations += new ValidationSpecification(
        name = validationId(node, prop.name().value(), "maximum"),
        message = message,
        ramlMessage = Some(message),
        oasMessage = Some(message),
        targetClass = Seq(node.id),
        propertyConstraints = Seq(PropertyConstraint(
          ramlPropertyId = prop.nodePropertyMapping().value(),
          name = validationId(node, prop.name().value(), "maximum") + "/prop",
          message = Some(message),
          maxInclusive = Some(maxValue.toString)
        ))
      )
    }

    if (prop.minCount().nonNull && prop.minCount().value() > 0) {
      val message = s"Property '${prop.name()}' is mandatory"
      validations += new ValidationSpecification(
        name = validationId(node, prop.name().value(), "required"),
        message = message,
        ramlMessage = Some(message),
        oasMessage = Some(message),
        targetClass = Seq(node.id),
        propertyConstraints = Seq(
          PropertyConstraint(
            ramlPropertyId = prop.nodePropertyMapping().value(),
            name = validationId(node, prop.name().value(), "required") + "/prop",
            message = Some(message),
            minCount = Some("1")
          ))
      )
    }

    if (!prop.allowMultiple().value() && prop.mapKeyProperty().isNullOrEmpty) {
      val message = s"Property '${prop.name()}' cannot have more than 1 value"
      validations += new ValidationSpecification(
        name = validationId(node, prop.name().value(), "notCollection"),
        message = message,
        ramlMessage = Some(message),
        oasMessage = Some(message),
        targetClass = Seq(node.id),
        propertyConstraints = Seq(
          PropertyConstraint(
            ramlPropertyId = prop.nodePropertyMapping().value(),
            name = validationId(node, prop.name().value(), "notCollection") + "/prop",
            message = Some(message),
            maxCount = Some("1")
          ))
      )
    }

    prop.pattern().option() match {
      case Some(pattern) =>
        val message = s"Property '${prop.name()}' must match pattern $pattern"
        validations += new ValidationSpecification(
          name = validationId(node, prop.name().value(), "pattern"),
          message = message,
          ramlMessage = Some(message),
          oasMessage = Some(message),
          targetClass = Seq(node.id),
          propertyConstraints = Seq(
            PropertyConstraint(
              ramlPropertyId = prop.nodePropertyMapping().value(),
              name = validationId(node, prop.name().value(), "pattern") + "/prop",
              message = Some(message),
              pattern = Some(pattern)
            ))
        )
      case _ => // ignore
    }

    if (prop.enum().nonEmpty) {
      val values  = prop.enum().map(_.value())
      val message = s"Property '${prop.name()}' must match some value in ${values.mkString(",")}"
      validations += new ValidationSpecification(
        name = validationId(node, prop.name().value(), "enum"),
        message = message,
        ramlMessage = Some(message),
        oasMessage = Some(message),
        targetClass = Seq(node.id),
        propertyConstraints = Seq(
          PropertyConstraint(
            ramlPropertyId = prop.nodePropertyMapping().value(),
            name = validationId(node, prop.name().value(), "enum") + "/prop",
            message = Some(message),
            in = values.map(_.toString)
          ))
      )
    }

    // ranges here
    if (prop.literalRange().nonNull) {
      val dataRange = prop.literalRange().value()
      dataRange match {

        case literal if literal.endsWith("number") || literal.endsWith("float") || literal.endsWith("double") =>
          val message = s"Property '${prop.name()}'  value must be of type ${(Namespace.Xsd + "integer")
            .iri()} or ${(Namespace.Xsd + "float").iri()}"
          validations += new ValidationSpecification(
            name = validationId(node, prop.name().value(), "dialectRange"),
            message = message,
            ramlMessage = Some(message),
            oasMessage = Some(message),
            targetClass = Seq(node.id),
            propertyConstraints = Seq(
              PropertyConstraint(
                ramlPropertyId = prop.nodePropertyMapping().value(),
                name = validationId(node, prop.name().value(), "dialectRange") + "/prop",
                message = Some(message),
                custom = Some((b: EntryBuilder, parentId: String) => {
                  b.entry(
                    (Namespace.Shacl + "or").iri(),
                    _.obj(_.entry(
                      "@list",
                      _.list { l =>
                        l.obj { v =>
                          v.entry((Namespace.Shacl + "datatype").iri(),
                                  _.obj(_.entry("@id", (Namespace.Xsd + "integer").iri().trim)))
                        }
                        l.obj { v =>
                          v.entry((Namespace.Shacl + "datatype").iri(),
                                  _.obj(_.entry("@id", (Namespace.Xsd + "double").iri().trim)))
                        }
                      }
                    ))
                  )
                })
              ))
          )

        case literal =>
          val message = s"Property '${prop.name()}'  value must be of type $dataRange"
          validations += new ValidationSpecification(
            name = validationId(node, prop.name().value(), "dataRange"),
            message = message,
            ramlMessage = Some(message),
            oasMessage = Some(message),
            targetClass = Seq(node.id),
            propertyConstraints = Seq(
              PropertyConstraint(
                ramlPropertyId = prop.nodePropertyMapping().value(),
                name = validationId(node, prop.name().value(), "dataRange") + "/prop",
                message = Some(message),
                datatype = Some(literal)
              ))
          )

      }
    }

    if (prop
          .objectRange()
          .nonEmpty && !prop.objectRange().map(_.value()).contains((Namespace.Meta + "anyNode").iri())) {
      val message = s"Property '${prop.name()}'  value must be of type ${prop.objectRange()}"
      validations += new ValidationSpecification(
        name = validationId(node, prop.name().value(), "objectRange"),
        message = message,
        ramlMessage = Some(message),
        oasMessage = Some(message),
        targetClass = Seq(node.id),
        propertyConstraints = Seq(
          PropertyConstraint(
            ramlPropertyId = prop.nodePropertyMapping().value(),
            name = validationId(node, prop.name().value(), "objectRange") + "/prop",
            message = Some(message),
            `class` = prop.objectRange().map(_.value())
          ))
      )

      // nested validations here
      prop.objectRange().foreach { nodeMapping =>
        dialect.findNodeMapping(nodeMapping.value()).foreach { mapping =>
          validations ++= emitEntityValidations(mapping)
        }
      }
    }

    validations.toList
  }

  private def validationId(dialectNode: NodeMapping, propName: String, constraint: String): String =
    Option(dialectNode.id) match {
      case Some(id) => s"${id}_${propName}_${constraint}_validation"
      case None     => throw new Exception("Cannot generate validation for dialect node without ID")
    }

}
