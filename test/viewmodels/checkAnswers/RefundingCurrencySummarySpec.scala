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

package viewmodels.checkAnswers

import base.SpecBase
import models.UserAnswers
import pages.RefundingCurrencyPage
import play.api.i18n.Messages

class RefundingCurrencySummarySpec extends SpecBase {

  "RefundingCurrencySummary" - {
    "must return a row when currency is set" in {
      val app = applicationBuilder().build()
      implicit val msgs = messages(app)

      val answers = emptyUserAnswers.set(RefundingCurrencyPage, "EUR").success.value

      val rowOpt = RefundingCurrencySummary.row(answers)

      rowOpt.isDefined mustBe true
      val repr = rowOpt.get.toString
      repr must include("change-which-currency")
    }

    "must return None when currency not set" in {
      val app = applicationBuilder().build()
      implicit val msgs = messages(app)

      val rowOpt = RefundingCurrencySummary.row(emptyUserAnswers)
      rowOpt mustBe None
    }
  }
}
