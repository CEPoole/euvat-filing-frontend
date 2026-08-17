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

package controllers

import base.SpecBase
import models.{CheckMode, NormalMode}
import pages.{TotalVatClaimPage, TotalVatPaidPage}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import views.html.VatClaimWarningView

class VatClaimWarningControllerSpec extends SpecBase {

  "VatClaimWarningController Controller" - {

    "must return OK and the correct view for a GET in NormalMode" in {
      val answers = emptyUserAnswers.set(TotalVatClaimPage, BigDecimal("100")).success.value
      val userAnswers = answers.set(TotalVatPaidPage, BigDecimal("99")).success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.VatClaimWarningController.onPageLoad(NormalMode).url)
        val result = route(application, request).value
        val view = application.injector.instanceOf[VatClaimWarningView]
        status(result) mustEqual OK
        contentAsString(result) mustEqual view(routes.TotalVatClaimController.onPageLoad(NormalMode), NormalMode, "€", BigDecimal(100))(
          request,
          messages(application)
        ).toString
      }
    }

    "redirect to error page if url hopping without the required session data" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.VatClaimWarningController.onPageLoad(NormalMode).url)
        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to next page on submit in NormalMode" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.VatClaimWarningController.onSubmit(NormalMode).url)
        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
      }
    }

    "must redirect to next page on submit in CheckMode" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.VatClaimWarningController.onSubmit(CheckMode).url)
        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER
        // Navigator in CheckMode will forward to CheckYourPurchaseDetails, but
        // the controller now uses navigator.nextPage to determine the destination
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
      }
    }
  }
}
