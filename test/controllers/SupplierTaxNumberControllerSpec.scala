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
import forms.SupplierTaxNumberFormProvider
import models.{InvoiceType, NormalMode, SupplierTaxNumber, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.{InvoiceTypePage, RefundingCountryNamePage, RefundingCountryPage, SupplierTaxNumberPage}
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.SupplierTaxNumberView

import scala.concurrent.Future

class SupplierTaxNumberControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute = Call("GET", "/foo")

  lazy val supplierTaxNumberRoute = routes.SupplierTaxNumberController.onPageLoad(NormalMode).url

  val formProvider = new SupplierTaxNumberFormProvider()
  val form = formProvider()
  def backLink: Call = routes.SupplierAddressController.onPageLoad(NormalMode)

  def germanUserAnswers: UserAnswers = UserAnswers(userAnswersId).set(RefundingCountryPage, "DE").success.value

  "SupplierTaxNumber Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(germanUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, supplierTaxNumberRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[SupplierTaxNumberView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, backLink, isSimplifiedInvoice = false)(request, messages(application)).toString
      }
    }

    "must return OK and the correct view for a GET when invoice type is simplified" in {

      val simplifiedUserAnswers = germanUserAnswers.set(InvoiceTypePage, InvoiceType.SimplifiedInvoice).success.value

      val application = applicationBuilder(userAnswers = Some(simplifiedUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, supplierTaxNumberRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[SupplierTaxNumberView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, backLink, isSimplifiedInvoice = true)(request, messages(application)).toString
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      val userAnswers = germanUserAnswers.set(SupplierTaxNumberPage, SupplierTaxNumber.values.head).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, supplierTaxNumberRoute)

        val view = application.injector.instanceOf[SupplierTaxNumberView]

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form.fill(SupplierTaxNumber.values.head), NormalMode, backLink, isSimplifiedInvoice = false)(
          request,
          messages(application)
        ).toString
      }
    }

    "must redirect to the next page when valid data is submitted" in {

      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(germanUserAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, supplierTaxNumberRoute)
            .withFormUrlEncodedBody(("value", SupplierTaxNumber.values.head.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(germanUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, supplierTaxNumberRoute)
            .withFormUrlEncodedBody(("value", "invalid value"))

        val boundForm = form.bind(Map("value" -> "invalid value"))

        val view = application.injector.instanceOf[SupplierTaxNumberView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, NormalMode, backLink, isSimplifiedInvoice = false)(request, messages(application)).toString
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, supplierTaxNumberRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, supplierTaxNumberRoute)
            .withFormUrlEncodedBody(("value", SupplierTaxNumber.values.head.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a GET if country is not Germany" in {

      val userAnswers = UserAnswers(userAnswersId).set(RefundingCountryPage, "AT").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, supplierTaxNumberRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if country is not Germany" in {

      val userAnswers = UserAnswers(userAnswersId).set(RefundingCountryPage, "AT").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, supplierTaxNumberRoute)
            .withFormUrlEncodedBody(("value", SupplierTaxNumber.values.head.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a GET if country is missing from session" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, supplierTaxNumberRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
