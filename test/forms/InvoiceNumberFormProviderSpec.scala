/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package forms

import forms.behaviours.StringFieldBehaviours
import org.scalacheck.Gen.{choose, listOfN, oneOf}
import play.api.data.FormError

class InvoiceNumberFormProviderSpec extends StringFieldBehaviours {

  val requiredKey = "invoiceNumber.error.required"
  val lengthKey = "invoiceNumber.error.length"
  val maxLength = 30

  val form = new InvoiceNumberFormProvider()()

  ".value" - {

    val fieldName = "value"

    val allowedChars: Seq[Char] =
      ('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9') ++ Seq(' ', '.', ',', '-', '(', ')', '/', '=', '!', '"', '%', '&', '*', ';', '<', '>', '\'', ':',
                                                          '+', '?', '#', '$', '@', '[', ']', '\\', '^', '_', '`', '{', '}', '|', '~')

    val genValidString = for {
      len   <- org.scalacheck.Gen.choose(1, maxLength)
      chars <- org.scalacheck.Gen.listOfN(len, org.scalacheck.Gen.oneOf(allowedChars))
    } yield chars.mkString

    // Exclude whitespace-only strings (generator may otherwise produce strings
    // comprised solely of spaces). Ensure at least one non-space character.
    val genNonWhitespaceString = genValidString.suchThat(s => s.exists(c => c != ' '))

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      genNonWhitespaceString
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength   = maxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(maxLength))
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )

    "not bind strings with invalid characters" in {
      val result = form.bind(Map(fieldName -> "INV£123")).apply(fieldName)
      result.errors.map(_.message) must contain("invoiceNumber.error.invalid")
    }
  }
}
