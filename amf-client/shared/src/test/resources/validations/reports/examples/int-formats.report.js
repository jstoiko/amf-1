Model: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml
Profile: RAML 1.0
Conforms? false
Number of results: 4

Level: Violation

- Source: http://a.ml/vocabularies/amf/validation#example-validation-error
  Message: should be integer
  Level: Violation
  Target: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml#/declarations/types/scalar/int/example/invalid
  Property: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml#/declarations/types/scalar/int/example/invalid
  Position: Some(LexicalInformation([(10,15)-(10,19)]))
  Location: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml

- Source: http://a.ml/vocabularies/amf/validation#example-validation-error
  Message: should be <= 127
  Level: Violation
  Target: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml#/declarations/types/scalar/int8/example/invalid
  Property: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml#/declarations/types/scalar/int8/example/invalid
  Position: Some(LexicalInformation([(16,15)-(16,18)]))
  Location: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml

- Source: http://a.ml/vocabularies/amf/validation#example-validation-error
  Message: should be <= 32767
  Level: Violation
  Target: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml#/declarations/types/scalar/int16/example/invalid
  Property: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml#/declarations/types/scalar/int16/example/invalid
  Position: Some(LexicalInformation([(22,15)-(22,20)]))
  Location: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml

- Source: http://a.ml/vocabularies/amf/validation#example-validation-error
  Message: should be <= 2147483647
  Level: Violation
  Target: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml#/declarations/types/scalar/int32/example/invalid
  Property: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml#/declarations/types/scalar/int32/example/invalid
  Position: Some(LexicalInformation([(28,15)-(28,25)]))
  Location: file://amf-client/shared/src/test/resources/validations/types/int-formats.raml
