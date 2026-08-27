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

import config.FrontendAppConfig
import forms.behaviours.{FieldBehaviours, StringFieldBehaviours}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.data.FormError

class RefundingCountryFormProviderSpec extends StringFieldBehaviours with FieldBehaviours with MockitoSugar {

  private val allowed = Map("DE" -> "Germany", "UK" -> "United Kingdom", "ZZ" -> "Some Country")
  private val mockConfig = mock[FrontendAppConfig]
  private val formProvider = new RefundingCountryFormProvider(mockConfig)
  private val form = formProvider()

  ".value" - {
    when(mockConfig.countriesInEU)
      .thenReturn(allowed)

    val fieldName = "value"

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, "refundingCountry.error.required")
    )

    "bind valid data" in {
      val validValues = Seq("DE", "UK")
      validValues.foreach { v =>
        val result = form.bind(Map(fieldName -> v)).apply(fieldName)
        result.value.value mustBe v
        result.errors mustBe empty
      }
    }

    "not bind invalid data" in {
      val invalidValues = Seq("United Kingdom", "Some Country")
      invalidValues.foreach { v =>
        val result = form.bind(Map(fieldName -> v)).apply(fieldName)
        result.value.value mustBe v
        result.errors.head.key mustBe "value"
        result.errors.head.messages mustBe Seq("refundingCountry.error.invalid")
      }
    }
  }

  ".valueTyped" - {
    when(mockConfig.countriesInEU)
      .thenReturn(allowed)

    val fieldName = "valueTyped"

    "bind valid data" in {
      val validValues = Seq("DE", "UK")
      validValues.foreach { v =>
        val result = form.bind(Map(fieldName -> v)).apply(fieldName)
        result.value.value mustBe v
        result.errors mustBe empty
      }
    }

    "also bind invalid data that a user may type" in {
      val validValues = Seq("United Kingdom", "Some Country")
      validValues.foreach { v =>
        val result = form.bind(Map(fieldName -> v)).apply(fieldName)
        result.value.value mustBe v
        result.errors mustBe empty
      }
    }
  }

}
