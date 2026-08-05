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
import forms.TotalVatClaimFormProvider
import models.{NormalMode, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.{RefundingCountryPage, RefundingCurrencyPage, TotalVatClaimPage}
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.TotalVatClaimView
import org.scalatest.TryValues.*
import play.api.data.Form

import scala.concurrent.Future

class TotalVatClaimControllerSpec extends SpecBase with MockitoSugar {

  val formProvider = new TotalVatClaimFormProvider()
  val form: Form[BigDecimal] = formProvider()

  def onwardRoute = Call("GET", "/foo")

  val validAnswer = BigDecimal("123.45")

  lazy val totalVatClaimRoute: String = routes.TotalVatClaimController.onPageLoad(NormalMode).url

  def backLink: Call = routes.TotalVatPaidController.onPageLoad(NormalMode)

  "TotalVatClaim Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, totalVatClaimRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[TotalVatClaimView]

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLink, "€")(request, messages(application)).toString)
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      val userAnswers = UserAnswers(userAnswersId).set(TotalVatClaimPage, validAnswer).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, totalVatClaimRoute)

        val view = application.injector.instanceOf[TotalVatClaimView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form.fill(validAnswer), NormalMode, backLink, "€")(request, messages(application)).toString)
      }
    }

    "must redirect to the next page when valid data is submitted" in {

      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, totalVatClaimRoute)
            .withFormUrlEncodedBody(("value", validAnswer.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, totalVatClaimRoute)
            .withFormUrlEncodedBody(("value", "invalid value"))

        val boundForm = form.bind(Map("value" -> "invalid value"))

        val view = application.injector.instanceOf[TotalVatClaimView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(boundForm, NormalMode, backLink, "€")(request, messages(application)).toString)
      }
    }

    "must have the correct back link" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, totalVatClaimRoute)

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include(routes.TotalVatPaidController.onPageLoad(NormalMode).url)
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, totalVatClaimRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, totalVatClaimRoute)
            .withFormUrlEncodedBody(("value", validAnswer.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must display the kr symbol when the chosen currency is Estonian Kroon" in {

      val userAnswers = UserAnswers(userAnswersId)
        .set(RefundingCountryPage, "EE")
        .success
        .value
        .set(RefundingCurrencyPage, "EEK")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, totalVatClaimRoute)

        val view = application.injector.instanceOf[TotalVatClaimView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLink, "kr")(request, messages(application)).toString)
      }
    }

    "must display the € symbol when the chosen currency is Euro for a multi-currency country" in {

      val userAnswers = UserAnswers(userAnswersId)
        .set(RefundingCountryPage, "EE")
        .success
        .value
        .set(RefundingCurrencyPage, "EUR")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, totalVatClaimRoute)

        val view = application.injector.instanceOf[TotalVatClaimView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLink, "€")(request, messages(application)).toString)
      }
    }

    "must display the kr symbol on the error page when invalid data is submitted" in {

      val userAnswers = UserAnswers(userAnswersId)
        .set(RefundingCountryPage, "EE")
        .success
        .value
        .set(RefundingCurrencyPage, "EEK")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, totalVatClaimRoute).withFormUrlEncodedBody(("value", "invalid value"))

        val boundForm = form.bind(Map("value" -> "invalid value"))

        val view = application.injector.instanceOf[TotalVatClaimView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(boundForm, NormalMode, backLink, "kr")(request, messages(application)).toString)
      }
    }
  }
}
