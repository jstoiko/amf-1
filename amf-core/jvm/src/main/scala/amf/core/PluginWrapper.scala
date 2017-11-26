package amf.core

import amf.core.model.domain
import amf.core.unsafe.PlatformSecrets
import amf.model.document.{Document, Fragment, Module}
import amf.model.domain.{CustomDomainProperty, DomainElement, DomainExtension, PropertyShape}

object PluginWrapper extends PlatformSecrets{

  def init() = {
    platform.registerWrapper(amf.core.metamodel.document.ModuleModel) {
      case m: model.document.Module => Module(m)
    }
    platform.registerWrapper(amf.core.metamodel.document.DocumentModel) {
      case m: model.document.Document => Document(m)
    }
    platform.registerWrapper(amf.core.metamodel.document.FragmentModel) {
      case f: model.document.Fragment => Fragment(f)
    }
    platform.registerWrapper(amf.core.metamodel.domain.DomainElementModel) {
      case e: model.domain.DomainElement => DomainElement(e)
    }
    platform.registerWrapper(amf.core.metamodel.domain.extensions.CustomDomainPropertyModel) {
      case e: domain.extensions.CustomDomainProperty => CustomDomainProperty(e)
    }
    platform.registerWrapper(amf.core.metamodel.domain.extensions.DomainExtensionModel) {
      case e: domain.extensions.DomainExtension => DomainExtension(e)
    }
    platform.registerWrapper(amf.core.metamodel.domain.extensions.PropertyShapeModel) {
      case e: domain.extensions.PropertyShape => PropertyShape(e)
    }
  }

}
