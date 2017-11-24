package amf.plugins.features.validation.model

import amf.plugins.document.vocabularies.model.domain.DomainEntity
import amf.framework.vocabulary.Namespace
import org.yaml.model.YDocument.EntryBuilder

import scala.collection.mutable

trait DialectWrapper {

  def expand(value: String, prefixes: mutable.Map[String,String]): String = {
    val valuePrefix = value.split("\\.").head
    prefixes.get(valuePrefix) match {
      case Some(uri) => value.replace(valuePrefix + ".", uri)
      case _         => value
    }
  }

  def extractString(node: DomainEntity, property: String): Option[String] = {
    node.definition.props.get(property) match {
      case Some(profileProperty) => node.string(profileProperty)
      case _                     => None
    }
  }

  def extractStrings(node: DomainEntity, property: String): Seq[String] = {
    node.definition.props.get(property) match {
      case Some(profileProperty) => node.strings(profileProperty)
      case None                  => Seq()
    }
  }

  def mapEntities[T](node: DomainEntity, property: String, f: (DomainEntity) => T): Seq[T] = {
    node.definition.props.get(property) match {
      case Some(profileProperty) => node.entities(profileProperty).map(f)
      case None                  => Seq()
    }
  }

  def mapEntity[T](node: DomainEntity, property: String, f: (DomainEntity) => T): Option[T] = {
    node.definition.props.get(property) match {
      case Some(profileProperty) => node.entity(profileProperty).map(f)
      case _                     => None
    }
  }

  def prefixes(node: DomainEntity) = {
    val prefixMap: mutable.Map[String,String] = mutable.HashMap()
    node.definition.props.get("prefixes") match {
      case Some(prefixesProperty) => node.entities(prefixesProperty) foreach  { prefixEntity =>
        val prefix = extractString(prefixEntity, "prefix").getOrElse("")
        val prefixUri = extractString(prefixEntity, "uri").getOrElse("")
        prefixMap.put(prefix, prefixUri)
      }
      case _ =>
    }
    prefixMap
  }

  def mandatory[T](message: String, x: Option[T]): T = x match {
    case Some(e) => e
    case None    => throw new Exception(s"Missing mandatory property $message")
  }

}

case class FunctionConstraint(message: Option[String],
                              code: Option[String] = None,
                              libraries: Seq[String] = Seq(),
                              functionName: Option[String] = None
                             ) {

  def constraintId(validationId: String) = s"${validationId}Constraint"
  def validatorId(validationId: String) = s"${validationId}Validator"
  def validatorPath(validationId: String) = s"${validationId}Path"
  def validatorArgument(validationId: String) = {
    "$" + validatorPath(validationId)
      .split("#")
      .last
      .replace("-","_")
      .replace(".","_")
  }
  def computeFunctionName(validationId: String) = functionName match {
    case Some(fnName) => fnName
    case _            => {
      val localName = validationId.split("/").last.split("#").last
      s"${localName.replace("-","_").replace(".","_")}FnName"
    }
  }
}

object FunctionConstraint extends DialectWrapper {
  def apply(node: DomainEntity): FunctionConstraint = {
    FunctionConstraint(
      message          = extractString(node, "message"),
      code             = extractString(node, "code"),
      libraries        = extractStrings(node, "libraries"),
      functionName     = extractString(node, "functionName"),
    )
  }
}

case class NodeConstraint(constraint: String, value: String)

case class PropertyConstraint(ramlPropertyId: String,
                              name: String,
                              message: Option[String] = None,
                              pattern: Option[String] = None,
                              maxCount: Option[String] = None,
                              minCount: Option[String] = None,
                              minLength: Option[String] = None,
                              maxLength: Option[String] = None,
                              minExclusive: Option[String] = None,
                              maxExclusive: Option[String] = None,
                              minInclusive: Option[String] = None,
                              maxInclusive: Option[String] = None,
                              node: Option[String] = None,
                              datatype: Option[String] = None,
                             `class`: Seq[String] = Seq(),
                              in: Seq[String] = Seq.empty,
                              custom: Option[(EntryBuilder, String) => Unit] = None
                             ) {}

object PropertyConstraint extends DialectWrapper {
  def apply(node: DomainEntity, prefixes: mutable.Map[String,String]): PropertyConstraint = {
    PropertyConstraint(
      ramlPropertyId      = expand(mandatory("ramlID in property constraint", node.linkValue), prefixes),
      name                = mandatory("name in property constraint", extractString(node, "name")),
      message             = extractString(node, "message"),
      pattern             = extractString(node, "pattern"),
      maxCount            = extractString(node, "maxCount"),
      minCount            = extractString(node, "minCount"),
      maxLength           = extractString(node, "maxLength"),
      minLength           = extractString(node, "minLength"),
      maxExclusive        = extractString(node, "maxExclusive"),
      minExclusive        = extractString(node, "minExclusive"),
      maxInclusive        = extractString(node, "maxInclusive"),
      minInclusive        = extractString(node, "minInclusive"),
      in                  = extractStrings(node, "in")
    )
  }
}

case class ValidationSpecification(name: String,
                                   message: String,
                                   ramlMessage: Option[String] = None,
                                   oasMessage: Option[String] = None,
                                   targetInstance: Seq[String] = Seq.empty,
                                   targetClass: Seq[String] = Seq.empty,
                                   targetObject: Seq[String] = Seq.empty,
                                   unionConstraints: Seq[String] = Seq.empty,
                                   propertyConstraints: Seq[PropertyConstraint] = Seq.empty,
                                   nodeConstraints: Seq[NodeConstraint] = Seq.empty,
                                   closed: Option[Boolean] = None,
                                   functionConstraint: Option[FunctionConstraint] = None,
                                   custom: Option[(EntryBuilder, String) => Unit] = None
                                  ) {

  def id(): String = {
    if (name.startsWith("http://") || name.startsWith("https://") || name.startsWith("file:")) {
      name
    } else {
      Namespace.expand(name).iri() match {
        case s if s.startsWith("http://") || s.startsWith("https://") || s.startsWith("file:") => s
        case s  => (Namespace.Data + s).iri()
      }
    }
  }

  def isParserSide() = targetClass.nonEmpty && targetClass.head == ValidationSpecification.PARSER_SIDE_VALIDATION
}

object ValidationSpecification extends DialectWrapper {

  val PARSER_SIDE_VALIDATION = (Namespace.Shapes + "ParserShape").iri()

  def apply(node: DomainEntity, prefixes: mutable.Map[String,String]): ValidationSpecification = {
    ValidationSpecification(
      name                = mandatory("name in validation specification", extractString(node, "name")),
      message             = mandatory("message in validation specification", extractString(node, "message")),
      targetClass         = extractStrings(node, "targetClass").map(expand(_, prefixes)),
      propertyConstraints = mapEntities(node, "propertyConstraints", PropertyConstraint(_, prefixes)),
      functionConstraint  = mapEntity(node, "functionConstraint", FunctionConstraint.apply)
    )
  }
}

case class ValidationProfile(name: String,
                             baseProfileName: Option[String],
                             violationLevel: Seq[String] = Seq.empty,
                             infoLevel: Seq[String] = Seq.empty,
                             warningLevel: Seq[String] = Seq.empty,
                             disabled: Seq[String] = Seq.empty,
                             validations: Seq[ValidationSpecification] = Seq.empty,
                             prefixes: mutable.Map[String,String] = mutable.Map.empty){}

object ValidationProfile extends DialectWrapper {
  def apply(node: DomainEntity): ValidationProfile = {
    val prfx = prefixes(node)
    ValidationProfile(
      name            = mandatory("profile in validation profile", extractString(node, "profile")),
      baseProfileName = extractString(node, "extends"),
      violationLevel  = extractStrings(node, "violation"),
      infoLevel       = extractStrings(node, "info"),
      warningLevel    = extractStrings(node, "warning"),
      disabled        = extractStrings(node, "disabled"),
      validations     = mapEntities(node, "validations", ValidationSpecification(_, prfx)),
      prefixes        = prfx
    )
  }
}