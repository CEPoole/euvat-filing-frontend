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

import forms.behaviours.FieldBehaviours
import play.api.data.FormError

class DescribeItemsOnInvoiceFormProviderSpec extends FieldBehaviours {

  private val form = new DescribeItemsOnInvoiceFormProvider().apply()

  private val fieldName = "value"
  private val requiredKey = "describeItemsOnInvoice.error.required"
  private val lengthKey = "describeItemsOnInvoice.error.length"
  private val maxLength = 255

  ".value" - {

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )

    "not bind a value longer than max length" in {
      val invalidValue = "a" * (maxLength + 1)
      val result = form.bind(Map(fieldName -> invalidValue)).apply(fieldName)
      result.errors mustEqual Seq(FormError(fieldName, lengthKey, Seq(maxLength)))
    }

    "bind a value at exactly max length" in {
      val validValue = "a" * maxLength
      val result = form.bind(Map(fieldName -> validValue)).apply(fieldName)
      result.errors mustBe empty
    }

    "bind a valid value below the max length" in {
      val result = form.bind(Map(fieldName -> "Fuel and transport costs"))
      result.errors mustBe empty
      result.value.value mustEqual "Fuel and transport costs"
    }
  }
}