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
import models.{CheckMode, Fuel, NormalMode, UserAnswers}
import pages.PurchaseTypePage
import models.PurchaseType
import org.mockito.Mockito.verify
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.{RefundingCountryPage, RefundingCurrencyPage, TotalPurchaseAmountBeforeVatPage, TotalVatClaimPage, TotalVatPaidPage}
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

  def onwardRoute: Call = Call("GET", "/foo")
  val validAnswer: BigDecimal = BigDecimal("123.45")

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
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form.fill(validAnswer), NormalMode, backLink, "€")(request, messages(application)).toString
        )
      }
    }

    "must redirect to the next page when valid data is submitted" in {
      val userAnswers = emptyUserAnswers.set(TotalVatPaidPage, BigDecimal(200)).success.value
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
        val request =
          FakeRequest(POST, totalVatClaimRoute)
            .withFormUrlEncodedBody(("value", validAnswer.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must redirect to the warning page if total vat paid is less than total vat claim" in {
      val userAnswers = emptyUserAnswers.set(TotalVatPaidPage, BigDecimal("100")).success.value
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
        val request =
          FakeRequest(POST, totalVatClaimRoute)
            .withFormUrlEncodedBody(("value", validAnswer.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.VatClaimWarningController.onPageLoad(NormalMode).url
      }
    }

    "must redirect to the warning page if total vat claim equals total purchase before VAT" in {
      val userAnswers = emptyUserAnswers
        .set(TotalVatPaidPage, BigDecimal("10"))
        .success
        .value
        .set(TotalPurchaseAmountBeforeVatPage, validAnswer)
        .success
        .value
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
        val request =
          FakeRequest(POST, totalVatClaimRoute)
            .withFormUrlEncodedBody(("value", validAnswer.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.VatClaimWarningController.onPageLoad(NormalMode).url
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
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(boundForm, NormalMode, backLink, "€")(request, messages(application)).toString
        )
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

    "must redirect to CYA when in CheckMode and total vat claim unchanged for purchase journey" in {
      val userAnswers = UserAnswers(userAnswersId)
        .set(PurchaseTypePage, Fuel)
        .success
        .value
        .set(TotalVatClaimPage, validAnswer)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.TotalVatClaimController.onSubmit(CheckMode).url)
          .withFormUrlEncodedBody(("value", validAnswer.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
      }
    }

    "must redirect to warning when in CheckMode and total vat claim unchanged but total vat paid triggers warning (arrived from prior page)" in {
      val userAnswers = UserAnswers(userAnswersId)
        .set(PurchaseTypePage, Fuel)
        .success
        .value
        .set(TotalVatClaimPage, BigDecimal("150.00"))
        .success
        .value
        .set(TotalVatPaidPage, BigDecimal("100.00"))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.TotalVatClaimController.onSubmit(CheckMode).url)
          .withHeaders("Referer" -> "/total-vat-paid")
          .withFormUrlEncodedBody(("value", "150.00"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
      }
    }

    "must persist updated total vat claim and continue journey when in CheckMode and total vat claim changed for purchase journey" in {
      val userAnswers = UserAnswers(userAnswersId)
        .set(PurchaseTypePage, Fuel)
        .success
        .value
        .set(TotalVatPaidPage, BigDecimal("200.00"))
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
        val request = FakeRequest(POST, routes.TotalVatClaimController.onSubmit(CheckMode).url)
          // Emulate that we were redirected from the prior page after editing it by setting the session marker
          .withSession("arrival" -> "total-purchase-before-vat")
          .withFormUrlEncodedBody(("value", validAnswer.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
        verify(mockSessionRepository).set(any())
      }
    }

    "must persist updated total vat claim and redirect to warning when in CheckMode and total vat claim > total vat paid" in {
      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, Fuel)
        .success
        .value
        .set(TotalVatPaidPage, BigDecimal("50.00"))
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
        val request =
          FakeRequest(POST, routes.TotalVatClaimController.onSubmit(CheckMode).url).withFormUrlEncodedBody(("value", validAnswer.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.VatClaimWarningController.onPageLoad(CheckMode).url
        verify(mockSessionRepository).set(any())
      }
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
      normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
        view(form, NormalMode, backLink, "kr")(request, messages(application)).toString
      )
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
      normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
        view(boundForm, NormalMode, backLink, "kr")(request, messages(application)).toString
      )
    }
  }
}
