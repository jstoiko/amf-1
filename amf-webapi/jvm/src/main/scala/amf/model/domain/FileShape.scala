package amf.model.domain

import amf.plugins.domain.shapes.models

import scala.collection.JavaConverters._

case class FileShape(private val file: models.FileShape) extends AnyShape(file) {

  def fileTypes: java.util.List[String] = file.fileTypes.asJava
  def pattern: String                = file.pattern
  def minLength: Int                 = file.minLength
  def maxLength: Int                 = file.maxLength
  def minimum: String                = file.minimum
  def maximum: String                = file.maximum
  def exclusiveMinimum: String       = file.exclusiveMinimum
  def exclusiveMaximum: String       = file.exclusiveMaximum
  def format: String                 = file.format
  def multipleOf: Int                = file.multipleOf

  def withFileTypes(fileTypes: java.util.List[String]): this.type = {
    file.withFileTypes(fileTypes.asScala)
    this
  }
  def withPattern(pattern: String): this.type = {
    file.withPattern(pattern)
    this
  }
  def withMinLength(min: Int): this.type = {
    file.withMinLength(min)
    this
  }
  def withMaxLength(max: Int): this.type = {
    file.withMaxLength(max)
    this
  }
  def withMinimum(min: String): this.type = {
    file.withMinimum(min)
    this
  }
  def withMaximum(max: String): this.type = {
    file.withMaximum(max)
    this
  }
  def withExclusiveMinimum(min: String): this.type = {
    file.withExclusiveMinimum(min)
    this
  }
  def withExclusiveMaximum(max: String): this.type = {
    file.withExclusiveMaximum(max)
    this
  }
  def withFormat(format: String): this.type = {
    file.withFormat(format)
    this
  }
  def withMultipleOf(multiple: Int): this.type = {
    file.withMultipleOf(multiple)
    this
  }

  override private[amf] def element = file

  override def linkTarget: Option[FileShape] =
    element.linkTarget.map({ case l: models.FileShape => FileShape(l) })

  override def linkCopy(): FileShape = FileShape(element.linkCopy())
}