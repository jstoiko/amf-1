package amf.core.services

import amf.core.annotations.LexicalInformation
import amf.core.model.document.BaseUnit
import amf.core.validation.core.ValidationReport
import amf.core.validation.{AMFValidationReport, EffectiveValidations}

import scala.concurrent.Future

/**
  * Validation of AMF models
  */
trait RuntimeValidator {

  /**
    * Loads a validation profile from a URL
    */
  def loadValidationProfile(validationProfilePath: String): Future[String]

  /**
    * Low level validation returning a SHACL validation report
    */
  def shaclValidation(model: BaseUnit,
                      validations: EffectiveValidations,
                      messageStyle: String): Future[ValidationReport]

  /**
    * Main validation function returning an AMF validation report linking validation errors
    * for validations in the profile to domain elements in the model
    */
  def validate(model: BaseUnit, profileName: String, messageStyle: String): Future[AMFValidationReport]

  def reset()

  def nestedValidation[T]()(k: => T): T

  /**
    * Client code can use this function to register a new validation failure
    */
  def reportConstraintFailure(level: String,
                              validationId: String,
                              targetNode: String,
                              targetProperty: Option[String] = None,
                              message: String = "",
                              position: Option[LexicalInformation] = None,
                              parserRun: Int)

  /**
    * Temporary disable checking of runtime validations for the duration of the passed block
    */
  def disableValidations[T]()(f: () => T): T

  /**
    * Async version of disable valdiations
    */
  def disableValidationsAsync[T]()(f: (() => Unit) => T): T
}

object RuntimeValidator {
  var validatorOption: Option[RuntimeValidator] = None
  def register(runtimeValidator: RuntimeValidator) = {
    validatorOption = Some(runtimeValidator)
  }

  private def validator: RuntimeValidator = {
    validatorOption match {
      case Some(runtimeValidator) => runtimeValidator
      case None                   => throw new Exception("No registered runtime validator")
    }
  }

  def loadValidationProfile(validationProfilePath: String) = validator.loadValidationProfile(validationProfilePath)

  def shaclValidation(model: BaseUnit,
                      validations: EffectiveValidations,
                      messgeStyle: String = "AMF"): Future[ValidationReport] =
    validator.shaclValidation(model, validations, messgeStyle)

  def apply(model: BaseUnit, profileName: String, messageStyle: String = "AMF"): Future[AMFValidationReport] =
    validator.validate(model, profileName, messageStyle)

  def reset() = validator.reset()

  def nestedValidation[T]()(k: => T): T = validator.nestedValidation()(k)


  def disableValidations[T]()(f: () => T): T = validator.disableValidations()(f)

  def disableValidationsAsync[T]()(f: (() => Unit) => T): T = validator.disableValidationsAsync()(f)

  def reportConstraintFailure(level: String,
                              validationId: String,
                              targetNode: String,
                              targetProperty: Option[String] = None,
                              message: String = "",
                              position: Option[LexicalInformation] = None,
                              parserRun: Int) = {
    validator.reportConstraintFailure(
      level,
      validationId,
      targetNode,
      targetProperty,
      message,
      position,
      parserRun
    )
  }
}
