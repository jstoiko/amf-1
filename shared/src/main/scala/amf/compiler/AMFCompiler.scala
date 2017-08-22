package amf.compiler

import amf.common.AMFToken.{Comment, Entry}
import amf.common.Strings.strings
import amf.common.{AMFAST, AMFToken}
import amf.compiler.AMFCompiler.RAML_10
import amf.document.{BaseUnit, Document}
import amf.domain.Annotation.LexicalInformation
import amf.domain.{Annotation, DomainElement}
import amf.exception.CyclicReferenceException
import amf.graph.GraphParser
import amf.json.JsonLexer
import amf.lexer.AbstractLexer
import amf.maker.WebApiMaker
import amf.oas.OasParser
import amf.parser.{BaseAMFParser, YeastASTBuilder}
import amf.raml.RamlParser
import amf.remote.Mimes._
import amf.remote.Syntax.{Json, Yaml}
import amf.remote._
import amf.serialization.AmfParser
import amf.yaml.YamlLexer

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.failed

class AMFCompiler private (val url: String,
                           val remote: Platform,
                           val base: Option[Context],
                           hint: Hint,
                           private val cache: Cache) {

  private lazy val context: Context                    = base.map(_.update(url)).getOrElse(Context(remote, url))
  private lazy val location                            = context.current
  private var ast: AMFAST                              = _
  private val references: ListBuffer[Future[BaseUnit]] = ListBuffer()

  def build(): Future[BaseUnit] = {
    if (context.hasCycles) failed(new CyclicReferenceException(context.history))
    else {
      cache.getOrUpdate(location) {
        compile
      }
    }
  }

  private def compile() = root().map(make)

  /** Usage at tests. */
  private[compiler] def root(): Future[Root] = {
    remote
      .resolve(location, base)
      .flatMap(parse)
  }

  def resolveLexer(content: Content): AbstractLexer[AMFToken] = {
    content.mime match {
      case Syntax(Yaml) => YamlLexer(content.stream)
      case Syntax(Json) => JsonLexer(content.stream)
      case _ =>
        hint.syntax match {
          case Yaml => YamlLexer(content.stream)
          case Json => JsonLexer(content.stream)
          case _    => ???
        }
    }
  }

  def resolveParser(builder: YeastASTBuilder, content: Content): BaseAMFParser = {
    content.mime match {
      case Some(`APPLICATION/RAML` | `APPLICATION/RAML+JSON` | `APPLICATION/RAML+YAML`) => new RamlParser(builder)
      case Some(
          `APPLICATION/OPENAPI+JSON` | `APPLICATION/SWAGGER+JSON` | `APPLICATION/OPENAPI+YAML` |
          `APPLICATION/SWAGGER+YAML` | `APPLICATION/OPENAPI` | `APPLICATION/SWAGGER`) =>
        new OasParser(builder)
      case _ =>
        hint.vendor match {
          case Raml => new RamlParser(builder)
          case Oas  => new OasParser(builder)
          case Amf  => new AmfParser(builder)
        }
    }
  }

  private def make(root: Root): BaseUnit = {
    root match {
      case Root(_, _, _, Oas)  => makeOasUnit(root)
      case Root(_, _, _, Raml) => makeRamlUnit(root)
      case Root(_, _, _, Amf)  => makeAmfUnit(root)
    }
  }

  private def createAmfUnit(root: Root): BaseUnit = null

  private def makeRamlUnit(root: Root): BaseUnit = {
    hint.kind match {
      case Library     => document(root.location, root.references, WebApiMaker(root).make) // TODO libraries
      case Link        => document(root.location, root.references, WebApiMaker(root).make) // TODO includes
      case Unspecified => resolveRamlUnit(root)
    }
  }

  private def resolveRamlUnit(root: Root) = {
    root.ast.head match {
      case c if c.is(Comment) && RAML_10 == c.content =>
        document(root.location, root.references, WebApiMaker(root).make)
      case _ => ???
    }
  }

  private def document(location: String, references: Seq[BaseUnit], element: DomainElement): Document = {
    Document()
      .adopted(location)
      .withLocation(location)
      .withReferences(references)
      .withEncodes(element)
  }

  private def makeOasUnit(root: Root): BaseUnit = {
    root.ast.head.children.find(e =>
      e.is(Entry) && e.head.content.unquote == "swagger" && e.last.content.unquote == "2.0") match {
      case Some(_) => document(root.location, root.references, WebApiMaker(root).make)
      case _       => ???
    }
  }

  private def makeAmfUnit(root: Root): BaseUnit = GraphParser.parse(root.ast, root.location)

  private def parse(content: Content) = {
    val lexer   = resolveLexer(content)
    val builder = YeastASTBuilder(lexer, content.url)
    val parser  = resolveParser(builder, content)

    if (ast == null) {
      ast = builder.root() {
        parser.parse
      }
    }

    builder.references.foreach(link => {
      references += link.resolve(remote, context, cache, hint)
    })

    Future.sequence(references).map(rs => Root(ast, content.url, rs, parser.vendor()))
  }
}

case class Root(ast: AMFAST, location: String, references: Seq[BaseUnit], vendor: Vendor) {
  def annotations(): List[Annotation] = List(LexicalInformation(ast.range))
}

object AMFCompiler {
  def apply(url: String, remote: Platform, hint: Hint, context: Option[Context] = None, cache: Option[Cache] = None) =
    new AMFCompiler(url, remote, context, hint, cache.getOrElse(Cache()))

  val RAML_10 = "#%RAML 1.0\n"
}
