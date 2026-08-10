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

import javax.inject.Inject
import play.api.data.Form
import play.api.data.Forms.*
import play.api.data.validation.{Constraint, Invalid, Valid}

class PurchaseSubTypeFormProvider @Inject() () {
  private def nonEmptyOpt(requiredKey: String): Constraint[Option[String]] = Constraint {
    case Some(s) if s.trim.nonEmpty => Valid
    case _                          => Invalid(requiredKey)
  }

  /** Create a form for a radio selection where the caller supplies the message key to use when the value is missing. We bind as an optional `text`
    * and apply a form-level constraint so the dynamic `requiredKey` is used even when the request omits the `value` parameter entirely (Play's
    * default missing-field error would otherwise use the static `error.required`).
    */
  def apply(requiredKey: String = "error.required"): Form[String] = {
    val mapping = optional(text)
      .verifying(nonEmptyOpt(requiredKey))
      .transform[String](opt => opt.getOrElse(""), s => Some(s))

    Form("value" -> mapping)
  }
}
