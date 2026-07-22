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
import forms.TotalPurchaseAmountBeforeVatFormProvider
import models.{CheckMode, NormalMode, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.TotalPurchaseAmountBeforeVatPage
import pages.RefundingCountryPage
import pages.RefundingCurrencyPage
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.TotalPurchaseAmountBeforeVatView

import scala.concurrent.Future

class TotalPurchaseAmountBeforeVatControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute = Call("GET", "/foo")

  val formProvider = new TotalPurchaseAmountBeforeVatFormProvider()
  val form = formProvider()

  lazy val totalPurchaseAmountBeforeVatRoute = routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode).url

  "TotalPurchaseAmountBeforeVat Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, totalPurchaseAmountBeforeVatRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[TotalPurchaseAmountBeforeVatView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, routes.SupplierVatRegistrationNumberController.onPageLoad(NormalMode), "€", "Euro")(
          request,
          messages(application)
        ).toString
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      val userAnswers = UserAnswers(userAnswersId).set(TotalPurchaseAmountBeforeVatPage, BigDecimal("12.34")).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, totalPurchaseAmountBeforeVatRoute)

        val view = application.injector.instanceOf[TotalPurchaseAmountBeforeVatView]

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form.fill(BigDecimal("12.34")),
                                               NormalMode,
                                               routes.SupplierVatRegistrationNumberController.onPageLoad(NormalMode),
                                               "€",
                                               "Euro"
                                              )(
          request,
          messages(application)
        ).toString
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
          FakeRequest(POST, totalPurchaseAmountBeforeVatRoute)
            .withFormUrlEncodedBody(("value", "123.45"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, totalPurchaseAmountBeforeVatRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[TotalPurchaseAmountBeforeVatView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm,
                                               NormalMode,
                                               routes.SupplierVatRegistrationNumberController.onPageLoad(NormalMode),
                                               "€",
                                               "Euro"
                                              )(
          request,
          messages(application)
        ).toString
      }
    }

    "must redirect to the next page when valid data is submitted in CheckMode" in {

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
          FakeRequest(POST, routes.TotalPurchaseAmountBeforeVatController.onSubmit(CheckMode).url)
            .withFormUrlEncodedBody(("value", "123.45"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, totalPurchaseAmountBeforeVatRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }

    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, totalPurchaseAmountBeforeVatRoute)
            .withFormUrlEncodedBody(("value", "123.45"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must show euro symbol for a country with a single currency" in {

      val userAnswers = UserAnswers(userAnswersId).set(RefundingCountryPage, "AT").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, totalPurchaseAmountBeforeVatRoute)

        val view = application.injector.instanceOf[TotalPurchaseAmountBeforeVatView]

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, routes.SupplierVatRegistrationNumberController.onPageLoad(NormalMode), "€", "Euro")(
          request,
          messages(application)
        ).toString
      }
    }

    "must show selected currency symbol when a currency is selected for a multi-currency country" in {

      val userAnswers = UserAnswers(userAnswersId)
        .set(RefundingCountryPage, "BG")
        .success
        .value
        .set(RefundingCurrencyPage, "BGN")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, totalPurchaseAmountBeforeVatRoute)

        val view = application.injector.instanceOf[TotalPurchaseAmountBeforeVatView]

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form,
                                               NormalMode,
                                               routes.SupplierVatRegistrationNumberController.onPageLoad(NormalMode),
                                               "лв",
                                               "Bulgarian Lev"
                                              )(request, messages(application)).toString
      }
    }

    "must fallback to first currency symbol when no currency selected for a multi-currency country" in {

      val userAnswers = UserAnswers(userAnswersId).set(RefundingCountryPage, "EE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, totalPurchaseAmountBeforeVatRoute)

        val view = application.injector.instanceOf[TotalPurchaseAmountBeforeVatView]

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, routes.SupplierVatRegistrationNumberController.onPageLoad(NormalMode), "€", "Euro")(
          request,
          messages(application)
        ).toString
      }
    }
  }
}
