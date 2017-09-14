package amf.compiler

import amf.common.AMFToken.MapToken
import amf.common.{AMFAST, AMFASTLink}
import amf.document.{BaseUnit, Document}
import amf.domain.WebApi
import amf.exception.CyclicReferenceException
import amf.remote.Syntax.{Json, Syntax, Yaml}
import amf.remote._
import amf.unsafe.PlatformSecrets
import org.scalatest.Matchers._
import org.scalatest.{Assertion, AsyncFunSuite}

import scala.concurrent.ExecutionContext

/**
  *
  */
class AMFCompilerTest extends AsyncFunSuite with PlatformSecrets {

  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  test("Api (raml)") {
    AMFCompiler("file://shared/src/test/resources/tck/raml-1.0/Api/test003/api.raml", platform, RamlYamlHint)
      .build() map assertDocument
  }

  test("Vocabulary") {
    AMFCompiler("file://shared/src/test/resources/vocabularies/raml_doc.raml", platform, RamlYamlHint).build().onComplete(unit => {
      assert(unit.isSuccess)
    })

    true shouldBe(true)
  }

  test("Api (oas)") {
    AMFCompiler("file://shared/src/test/resources/tck/raml-1.0/Api/test003/api.openapi", platform, OasJsonHint)
      .build() map assertDocument
  }

  test("Api (amf)") {
    AMFCompiler("file://shared/src/test/resources/tck/raml-1.0/Api/test003/api.jsonld", platform, AmfJsonHint)
      .build() map assertDocument
  }

  test("Simple import") {
    AMFCompiler("file://shared/src/test/resources/input.json", platform, OasJsonHint)
      .build() map {
      _ should not be null
    }
  }

  test("Reference in imports with cycles (json)") {
    assertCycles(Json, OasJsonHint)
  }

  test("Reference in imports with cycles (yaml)") {
    assertCycles(Yaml, RamlYamlHint)
  }

  test("Cache duplicate imports") {
    val cache = new TestCache()
    AMFCompiler("file://shared/src/test/resources/input-duplicate-includes.json",
                platform,
                OasJsonHint,
                cache = Some(cache))
      .build() map { _ =>
      cache.assertCacheSize(2)
    }
  }

  test("Cache different imports") {
    val cache = new TestCache()
    AMFCompiler("file://shared/src/test/resources/input.json", platform, OasJsonHint, cache = Some(cache))
      .build() map { _ =>
      cache.assertCacheSize(3)
    }
  }

  test("Libraries (raml)") {
    val cache = new TestCache()
    AMFCompiler("file://shared/src/test/resources/modules.raml", platform, RamlYamlHint, cache = Some(cache))
      .root() map {
      case Root(root, _, _, _) =>
        root.children.size should be(2)
        val bodyMap = root > MapToken
        bodyMap.children.size should be(2)
        val uses = bodyMap.children(1)
        assertUses(uses)
    }
  }

  test("Libraries (oas)") {
    val cache = new TestCache()
    AMFCompiler("file://shared/src/test/resources/modules.json", platform, OasJsonHint, cache = Some(cache))
      .root() map {
      case Root(root, _, _, _) =>
        root.children.size should be(1)
        val bodyMap = root.children.head
        bodyMap.children.size should be(3)
        val uses = bodyMap.children(2)
        assertUses(uses)
    }
  }

  private def assertDocument(unit: BaseUnit): Assertion = unit match {
    case d: Document =>
      d.encodes.asInstanceOf[WebApi].host should be("api.example.com")
      d.encodes.asInstanceOf[WebApi].name should be("test")
  }

  private def assertUses(uses: AMFAST) = {
    uses.children.length should be(2)
    uses.children.head.content should include("uses")
    val libraries = uses.children(1)
    libraries.children.length should be(2)

    val library = libraries.children.head
    library.children.size should be(2)
    library.children(1) shouldBe a[AMFASTLink]
    val link = library.children(1).asInstanceOf[AMFASTLink]
    link.target shouldBe a[Document]
  }

  private def assertCycles(syntax: Syntax, hint: Hint) = {
    recoverToExceptionIf[CyclicReferenceException] {
      AMFCompiler(s"file://shared/src/test/resources/input-cycle.${syntax.extension}", platform, hint)
        .build()
    } map { ex =>
      assert(ex.getMessage ==
        s"Cyclic found following references file://shared/src/test/resources/input-cycle.${syntax.extension} -> file://shared/src/test/resources/includes/include-cycle.${syntax.extension} -> file://shared/src/test/resources/input-cycle.${syntax.extension}")
    }
  }

  private class TestCache extends Cache {
    def assertCacheSize(expectedSize: Int): Assertion = {
      size should be(expectedSize)
    }
  }

}
