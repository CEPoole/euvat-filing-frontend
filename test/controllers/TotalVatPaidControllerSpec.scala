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
import forms.TotalVatPaidFormProvider
import models.{CheckMode, NormalMode, UserAnswers, Fuel}
import pages.PurchaseTypePage
import models.PurchaseType
import org.mockito.Mockito.verify
import pages.{TotalPurchaseAmountBeforeVatPage, TotalVatPaidPage}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.data.Form
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.TotalVatPaidView

import scala.concurrent.Future

class TotalVatPaidControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute: Call = Call("GET", "/foo")

  val formProvider = new TotalVatPaidFormProvider()
  val form: Form[BigDecimal] = formProvider()

  lazy val url: String = routes.TotalVatPaidController.onPageLoad(NormalMode).url

  "TotalVatPaid Controller" - {

    "must return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[TotalVatPaidView]

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode), "€", "Euro")(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must pre-fill the form when saved answers exist" in {
      val answers = emptyUserAnswers.set(TotalVatPaidPage, BigDecimal("12.34")).success.value

      val application = applicationBuilder(userAnswers = Some(answers)).build()

      running(application) {
        val request = FakeRequest(GET, url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[TotalVatPaidView]

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form.fill(BigDecimal("12.34")), NormalMode, routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode), "€", "Euro")(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must redirect to the next page when valid data is submitted" in {
      val userAnswers = emptyUserAnswers.set(TotalPurchaseAmountBeforeVatPage, BigDecimal("150")).success.value
      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request = FakeRequest(POST, url).withFormUrlEncodedBody(("value", "123.45"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must redirect to the warning page if Total paid amount is greater than Total purchase amount" in {
      val userAnswers = emptyUserAnswers.set(TotalPurchaseAmountBeforeVatPage, BigDecimal("120")).success.value
      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request = FakeRequest(POST, url).withFormUrlEncodedBody(("value", "200.45"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.VatPaidWarningController.onPageLoad(NormalMode).url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, url)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[TotalVatPaidView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(boundForm, NormalMode, routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode), "€", "Euro")(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must redirect to Journey Recovery when no existing data is found on GET" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery when no existing data is found on POST" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, url)
            .withFormUrlEncodedBody(("value", "123.45"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }
    "must redirect to CYA when in CheckMode and VAT paid unchanged for purchase journey" in {
      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, Fuel)
        .success
        .value
        .set(TotalVatPaidPage, BigDecimal("50.00"))
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.TotalVatPaidController.onSubmit(CheckMode).url)
          .withFormUrlEncodedBody(("value", "50.00"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
      }
    }

    "must redirect to warning when in CheckMode and VAT paid unchanged but total purchase amount triggers warning (arrived from prior page)" in {
      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, Fuel)
        .success
        .value
        .set(TotalVatPaidPage, BigDecimal("60.00"))
        .success
        .value
        .set(TotalPurchaseAmountBeforeVatPage, BigDecimal("50.00"))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.TotalVatPaidController.onSubmit(CheckMode).url)
          // arrived from prior amount page
          .withHeaders("Referer" -> "/total-purchase-amount-before-vat")
          .withFormUrlEncodedBody(("value", "60.00"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
      }
    }

    "must persist updated VAT paid and continue journey when in CheckMode and VAT paid changed for purchase journey" in {
      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, Fuel)
        .success
        .value
        .set(TotalPurchaseAmountBeforeVatPage, BigDecimal("200.00"))
        .success
        .value

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[repositories.SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request = FakeRequest(POST, routes.TotalVatPaidController.onSubmit(CheckMode).url)
          // Emulate that we were redirected from the prior page after editing it by setting the session marker
          .withSession("arrival" -> "total-purchase-before-vat")
          .withFormUrlEncodedBody(("value", "60.00"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
        verify(mockSessionRepository).set(any())
      }
    }

    "must persist updated VAT paid and redirect to warning when in CheckMode and VAT paid >= total purchase amount" in {
      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, Fuel)
        .success
        .value
        .set(TotalPurchaseAmountBeforeVatPage, BigDecimal("50.00"))
        .success
        .value

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[repositories.SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request = FakeRequest(POST, routes.TotalVatPaidController.onSubmit(CheckMode).url).withFormUrlEncodedBody(("value", "60.00"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.VatPaidWarningController.onPageLoad(CheckMode).url
        verify(mockSessionRepository).set(any())
      }
    }
  }
}
