package amf.framework.metamodel.domain

import amf.framework.metamodel.Type.{Iri, Str}
import amf.framework.metamodel.{Field, Obj}
import amf.framework.vocabulary.{Namespace, ValueType}

/**
  *
  */
trait LinkableElementModel extends Obj {

  val TargetId = Field(Iri, Namespace.Document + "link-target")
  val Label    = Field(Str, Namespace.Document + "link-label")

}

object LinkableElementModel extends LinkableElementModel {

  // 'Static' values, we know the element schema before parsing
  // If the domain element is dynamic, the value from the model,
  // not the meta-model, should be retrieved instead

  override val `type`: List[ValueType] = List(Namespace.Document + "Linkable")

  override val fields: List[Field] = List(TargetId, Label)

}