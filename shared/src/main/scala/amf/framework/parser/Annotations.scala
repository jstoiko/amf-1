package amf.framework.parser

import amf.framework.domain.{Annotation, LexicalInformation, SerializableAnnotation}
import amf.parser.Range
import amf.plugins.domain.webapi.models.annotations.SourceAST
import org.yaml.model.YPart

import scala.collection.mutable

/**
  * Element annotations
  */
class Annotations {
  private val annotations: mutable.ListBuffer[Annotation] = mutable.ListBuffer()

  def foreach(fn: (Annotation) => Unit): Unit = annotations.foreach(fn)

  def find[T <: Annotation](clazz: Class[T]): Option[T] = annotations.find(clazz.isInstance(_)).map(_.asInstanceOf[T])

  def contains[T <: Annotation](clazz: Class[T]): Boolean = find(clazz).isDefined

  def +=(annotation: Annotation): this.type = {
    annotations += annotation
    this
  }

  def ++=(other: Annotations): this.type = {
    annotations ++= other.annotations
    this
  }

  def reject(p:(Annotation) => Boolean): this.type = {
    annotations --= annotations.filter(p)
    this
  }

  /** Return [[SerializableAnnotation]]s only. */
  def serializables(): Seq[SerializableAnnotation] =
    annotations.filter(_.isInstanceOf[SerializableAnnotation]).map(_.asInstanceOf[SerializableAnnotation])
}

object Annotations {

  def apply(): Annotations = new Annotations()

  def apply(ast: YPart): Annotations = apply() += LexicalInformation(Range(ast.range)) += SourceAST(ast)
}