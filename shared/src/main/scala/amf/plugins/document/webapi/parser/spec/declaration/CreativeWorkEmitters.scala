package amf.plugins.document.webapi.parser.spec.declaration

import amf.framework.parser.Position
import amf.plugins.document.webapi.parser.spec.common.BaseEmitters.ValueEmitter
import amf.plugins.document.webapi.parser.spec.{EntryEmitter, PartEmitter, SpecOrdering}
import amf.plugins.document.webapi.parser.spec.common.SpecEmitterContext
import amf.plugins.domain.webapi.metamodel.CreativeWorkModel
import amf.plugins.domain.webapi.models.CreativeWork
import amf.plugins.document.webapi.parser.spec.common.BaseEmitters._
import org.yaml.model.YDocument.{EntryBuilder, PartBuilder}


import scala.collection.mutable.ListBuffer

/**
  *
  */
case class RamlCreativeWorkItemsEmitter(documentation: CreativeWork, ordering: SpecOrdering, withExtention: Boolean)(
    implicit spec: SpecEmitterContext) {
  def emitters(): Seq[EntryEmitter] = {
    val result = ListBuffer[EntryEmitter]()

    val fs = documentation.fields

    fs.entry(CreativeWorkModel.Url).map(f => result += ValueEmitter(if (withExtention) "(url)" else "url", f))

    fs.entry(CreativeWorkModel.Description).map(f => result += ValueEmitter("content", f))

    fs.entry(CreativeWorkModel.Title).map(f => result += ValueEmitter("title", f))

    result ++= AnnotationsEmitter(documentation, ordering).emitters
    ordering.sorted(result)
  }
}

case class RamlCreativeWorkEmitter(documentation: CreativeWork, ordering: SpecOrdering, withExtension: Boolean)(
    implicit spec: SpecEmitterContext)
    extends PartEmitter {
  override def emit(b: PartBuilder): Unit = {
    sourceOr(
      documentation.annotations,
      b.obj(traverse(RamlCreativeWorkItemsEmitter(documentation, ordering, withExtension).emitters(), _))
    )
  }

  override def position(): Position = pos(documentation.annotations)
}

case class OasCreativeWorkItemsEmitter(document: CreativeWork, ordering: SpecOrdering)(
    implicit spec: SpecEmitterContext) {
  def emitters(): Seq[EntryEmitter] = {
    val fs     = document.fields
    val result = ListBuffer[EntryEmitter]()

    fs.entry(CreativeWorkModel.Url).map(f => result += ValueEmitter("url", f))

    fs.entry(CreativeWorkModel.Description).map(f => result += ValueEmitter("description", f))

    fs.entry(CreativeWorkModel.Title).map(f => result += ValueEmitter("x-title", f))

    result ++= AnnotationsEmitter(document, ordering).emitters

    ordering.sorted(result)
  }
}

case class OasCreativeWorkEmitter(document: CreativeWork, ordering: SpecOrdering)(implicit spec: SpecEmitterContext)
    extends PartEmitter {
  override def emit(b: PartBuilder): Unit = {
    if (document.isLink)
      raw(b, document.linkLabel.getOrElse(document.linkTarget.get.id))
    else
      b.obj(traverse(OasCreativeWorkItemsEmitter(document, ordering).emitters(), _))
  }

  override def position(): Position = pos(document.annotations)
}

case class OasEntryCreativeWorkEmitter(key: String, documentation: CreativeWork, ordering: SpecOrdering)(
    implicit spec: SpecEmitterContext)
    extends EntryEmitter {
  override def emit(b: EntryBuilder): Unit = {
    sourceOr(
      documentation.annotations,
      b.entry(
        key,
        OasCreativeWorkEmitter(documentation, ordering).emit(_)
      )
    )
  }

  override def position(): Position = pos(documentation.annotations)
}