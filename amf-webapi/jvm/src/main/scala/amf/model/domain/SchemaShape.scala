package amf.model.domain

import amf.plugins.domain.shapes.models

case class SchemaShape(private[amf] val schema: models.SchemaShape) extends AnyShape(schema) {

  def mediaType: String = schema.mediaType
  def raw: String       = schema.raw

  def withMediatype(mediaType: String): this.type = {
    schema.withMediaType(mediaType)
    this
  }

  def withRaw(text: String): this.type = {
    schema.withRaw(text)
    this
  }

  override private[amf] def element = schema

  override def linkTarget: Option[DomainElement with Linkable] =
    element.linkTarget.map({ case l: models.ScalarShape => ScalarShape(l) })

  override def linkCopy() = SchemaShape(element.linkCopy())
}