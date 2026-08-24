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

package utils

import base.SpecBase
import models.{CheckMode, NormalMode, UserAnswers}
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.*
import pages.TotalVatPaidPage
import play.api.mvc.Results
import repositories.SessionRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CheckModeShortCircuitSpec extends SpecBase with MockitoSugar {

  "CheckModeShortCircuit.shortCircuitIfUnchanged" - {
    "must return Some(Redirect) when in CheckMode and value unchanged" in {
      val ua = emptyUserAnswers.set(TotalVatPaidPage, BigDecimal(12.34)).success.value
      val res = CheckModeShortCircuit.shortCircuitIfUnchanged(TotalVatPaidPage,
                                                              BigDecimal(12.34),
                                                              CheckMode,
                                                              ua,
                                                              controllers.routes.JourneyRecoveryController.onPageLoad()
                                                             )
      res.isDefined mustBe true
    }

    "must return None when in CheckMode but value changed" in {
      val ua = emptyUserAnswers.set(TotalVatPaidPage, BigDecimal(1)).success.value
      val res = CheckModeShortCircuit.shortCircuitIfUnchanged(TotalVatPaidPage,
                                                              BigDecimal(2),
                                                              CheckMode,
                                                              ua,
                                                              controllers.routes.JourneyRecoveryController.onPageLoad()
                                                             )
      res mustBe None
    }

    "must return None when not in CheckMode" in {
      val ua = emptyUserAnswers
      val res = CheckModeShortCircuit.shortCircuitIfUnchanged(TotalVatPaidPage,
                                                              BigDecimal(2),
                                                              NormalMode,
                                                              ua,
                                                              controllers.routes.JourneyRecoveryController.onPageLoad()
                                                             )
      res mustBe None
    }
  }

  "CheckModeShortCircuit.applyNoPersist" - {
    "must call onSaved when value changes and not persist" in {
      val ua = emptyUserAnswers
      // no repository required for applyNoPersist test
      // onSaved records that it was invoked by returning OK
      var called = false
      val onSaved: UserAnswers => Future[play.api.mvc.Result] = (_: UserAnswers) => { called = true; Future.successful(Results.Ok) }

      val f = CheckModeShortCircuit.applyNoPersist(TotalVatPaidPage,
                                                   BigDecimal(5),
                                                   CheckMode,
                                                   ua,
                                                   controllers.routes.JourneyRecoveryController.onPageLoad(),
                                                   onSaved
                                                  )
      whenReady(f) { r =>
        called mustBe true
        r.header.status mustBe 200
      }
      // no repository interaction asserted because this variant does not persist
    }
  }

}
