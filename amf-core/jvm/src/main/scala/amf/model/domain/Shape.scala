package amf.model.domain

import amf.core.model.domain
import amf.core.unsafe.PlatformSecrets

import scala.collection.JavaConverters._


abstract class Shape(private[amf] val shape: domain.Shape) extends DomainElement with Linkable {

  val name: String                       = shape.name
  val displayName: String                = shape.displayName
  val description: String                = shape.description
  val default: String                    = shape.default
  val values: java.util.List[String]     = shape.values.asJava
  val inherits: java.util.List[Shape]    = shape.inherits.map(platform.wrap(_)).asJava

  def withName(name: String): this.type = {
    shape.withName(name)
    this
  }
  def withDisplayName(name: String): this.type = {
    shape.withDisplayName(name)
    this
  }
  def withDescription(description: String): this.type = {
    shape.withDescription(description)
    this
  }
  def withDefault(default: String): this.type = {
    shape.withDefault(default)
    this
  }
  def withValues(values: java.util.List[String]): this.type = {
    shape.withValues(values.asScala)
    this
  }

  def withInherits(inherits: java.util.List[Shape]): this.type = {
    shape.withInherits(inherits.asScala.map(_.shape))
    this
  }

}

object Shape extends PlatformSecrets{
  def apply(shape: domain.Shape): Shape = platform.wrap(shape)
}