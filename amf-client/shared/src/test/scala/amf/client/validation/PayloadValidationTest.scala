package amf.client.validation

import amf.client.convert.NativeOps
import amf.client.convert.CoreClientConverters._
import amf.client.model.DataTypes
import amf.client.model.domain.{ArrayShape, NodeShape, PropertyShape, ScalarShape}
import amf.core.AMF
import amf.plugins.document.webapi.validation.PayloadValidatorPlugin
import org.scalatest.{AsyncFunSuite, Matchers}

import scala.concurrent.ExecutionContext

trait ClientPayloadValidationTest extends AsyncFunSuite with NativeOps with Matchers {

  test("Test parameter validator int payload") {
    AMF.init().flatMap { _ =>
      amf.Core.registerPlugin(PayloadValidatorPlugin)

      val test = new ScalarShape().withDataType(DataTypes.String).withName("test")

      test
        .parameterValidator("application/yaml")
        .asOption
        .get
        .validate("application/yaml", "1234")
        .asFuture
        .map(r => assert(r.conforms))
    }
  }

  test("Test parameter validator boolean payload") {
    AMF.init().flatMap { _ =>
      amf.Core.registerPlugin(PayloadValidatorPlugin)

      val test = new ScalarShape().withDataType(DataTypes.String).withName("test")

      test
        .parameterValidator("application/yaml")
        .asOption
        .get
        .validate("application/yaml", "true")
        .asFuture
        .map(r => assert(r.conforms))
    }
  }

  test("Invalid trailing coma in json object payload") {
    amf.Core.init().asFuture.flatMap { _ =>
      amf.Core.registerPlugin(PayloadValidatorPlugin)

      val s     = new ScalarShape().withDataType(DataTypes.String)
      val shape = new NodeShape().withName("person")
      shape.withProperty("someString").withRange(s)

      val payload =
        """
          |{
          |  "someString": "invalid string value",
          |}
        """.stripMargin

      shape
        .payloadValidator("application/json")
        .asOption
        .get
        .validate("application/json", payload)
        .asFuture
        .map(r => assert(!r.conforms))
    }
  }

  test("Invalid trailing coma in json array payload") {
    amf.Core.init().asFuture.flatMap { _ =>
      amf.Core.registerPlugin(PayloadValidatorPlugin)

      val s     = new ScalarShape().withDataType(DataTypes.String)
      val array = new ArrayShape().withName("person")
      array.withItems(s)

      val payload =
        """
          |["trailing", "comma",]
        """.stripMargin

      array
        .payloadValidator("application/json")
        .asOption
        .get
        .validate("application/json", payload)
        .asFuture
        .map(r => assert(!r.conforms))
    }
  }

  test("'null' doesn't conform as string") {
    amf.Core.init().asFuture.flatMap { _ =>
      amf.Core.registerPlugin(PayloadValidatorPlugin)
      val payload = "null"
      val validator =
        new ScalarShape().withDataType(DataTypes.String).payloadValidator("application/yaml").asOption.get
      validator.validate("application/yaml", payload).asFuture.map(r => r.conforms shouldBe false)
    }
  }

  test("'null' conforms as null") {
    amf.Core.init().asFuture.flatMap { _ =>
      amf.Core.registerPlugin(PayloadValidatorPlugin)
      val payload   = "null"
      val validator = new ScalarShape().withDataType(DataTypes.Nil).payloadValidator("application/yaml").asOption.get
      validator.validate("application/yaml", payload).asFuture.map(r => r.conforms shouldBe true)
    }
  }

  test("Big number against scalar shape") {
    amf.Core.init().asFuture.flatMap { _ =>
      amf.Core.registerPlugin(PayloadValidatorPlugin)
      val payload = "22337203685477999090"
      val validator =
        new ScalarShape().withDataType(DataTypes.Number).payloadValidator("application/json").asOption.get
      validator.validate("application/json", payload).asFuture.map(r => r.conforms shouldBe true)
    }
  }

  test("Very big number against scalar shape") {
    amf.Core.init().asFuture.flatMap { _ =>
      amf.Core.registerPlugin(PayloadValidatorPlugin)
      val payload = "22e20000"
      val validator =
        new ScalarShape().withDataType(DataTypes.Number).payloadValidator("application/json").asOption.get
      validator.validate("application/json", payload).asFuture.map(r => r.conforms shouldBe true)
    }
  }

  test("Big number against node shape") {
    amf.Core.init().asFuture.flatMap { _ =>
      amf.Core.registerPlugin(PayloadValidatorPlugin)
      val payload =
        """
          |{
          | "in": 22337203685477999090
          |}
          |""".stripMargin
      val properties = new PropertyShape()
        .withName("in")
        .withRange(new ScalarShape().withDataType(DataTypes.Number))
      val shape = new NodeShape()
        .withProperties(Seq(properties._internal).asClient)
      val validator = shape.payloadValidator("application/json").asOption.get

      validator.validate("application/json", payload).asFuture.map(r => r.conforms shouldBe true)
    }
  }

  override implicit def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}
