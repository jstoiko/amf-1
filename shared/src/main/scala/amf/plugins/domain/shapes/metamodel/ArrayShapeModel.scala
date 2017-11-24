package amf.plugins.domain.shapes.metamodel

import amf.framework.metamodel.Field
import amf.framework.metamodel.Type.{Bool, Int, SortedArray}
import amf.framework.metamodel.domain.DomainElementModel
import amf.plugins.domain.shapes.models.{ArrayShape, MatrixShape, TupleShape}
import amf.framework.vocabulary.Namespace.{Shacl, Shapes}
import amf.framework.vocabulary.ValueType

/**
  * Array shape metamodel
  */

/**
  * Common fields to all arrays, matrix and tuples
  */
trait DataArrangementShape {
  val Items = Field(ShapeModel, Shapes + "items")

  val MinItems = Field(Int, Shacl + "minCount")

  val MaxItems = Field(Int, Shacl + "maxCount")

  val UniqueItems = Field(Bool, Shapes + "uniqueItems")

  val fields: List[Field] = List(Items,
    MinItems,
    MaxItems,
    UniqueItems) ++ ShapeModel.fields ++ DomainElementModel.fields
}

object ArrayShapeModel extends DataArrangementShape with  ShapeModel with DomainElementModel {

  override val `type`: List[ValueType] = List(Shapes + "ArrayShape") ++ ShapeModel.`type` ++ DomainElementModel.`type`

  override def modelInstance = ArrayShape()
}

object MatrixShapeModel extends DataArrangementShape with  ShapeModel with DomainElementModel {
  override val `type`: List[ValueType] = List(Shapes + "MatrixShape", Shapes + "ArrayShape") ++ ShapeModel.`type` ++ DomainElementModel.`type`
  override def modelInstance = MatrixShape()
}

object TupleShapeModel extends DataArrangementShape with  ShapeModel with DomainElementModel {
  override val Items = Field(SortedArray(ShapeModel), Shapes + "items")
  override val `type`: List[ValueType] = List(Shapes + "TupleShape", Shapes + "ArrayShape") ++ ShapeModel.`type` ++ DomainElementModel.`type`
  override def modelInstance = TupleShape()
}
