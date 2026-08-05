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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import pages.TotalVatPaidPage
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.VatPaidWarningView

import scala.concurrent.Future

class VatPaidWarningControllerSpec extends SpecBase {

  "VatPaidWarningController Controller" - {

    "must return OK and the correct view for a GET in NormalMode" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.VatPaidWarningController.onPageLoad(NormalMode).url)
        val result = route(application, request).value
        val view = application.injector.instanceOf[VatPaidWarningView]
        status(result) mustEqual OK
        contentAsString(result) mustEqual view(routes.TotalVatPaidController.onPageLoad(NormalMode), NormalMode)(request,
                                                                                                                 messages(application)
                                                                                                                ).toString
      }
    }

    "must return OK and the correct view for a GET in NormalMode with pre-populate form" in {
      val answers = emptyUserAnswers.set(TotalVatPaidPage, BigDecimal(25.50)).success.value
      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
      val application =
        applicationBuilder(userAnswers = Some(answers))
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

      running(application) {
        val request = FakeRequest(GET, routes.VatPaidWarningController.onPageLoad(NormalMode).url)
        val result = route(application, request).value
        status(result) mustEqual OK
      }
    }

    "must redirect to next page on submit in NormalMode" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.VatPaidWarningController.onSubmit(NormalMode).url)
        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.TotalVatClaimController.onPageLoad(NormalMode).url
      }
    }

    "must redirect to check your purchase details page on submit in CheckMode" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.VatPaidWarningController.onSubmit(CheckMode).url)
        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url // TODO - Check your purchase details
      }
    }
  }
}
