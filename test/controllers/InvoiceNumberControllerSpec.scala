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
import forms.InvoiceNumberFormProvider
import models.{Fuel, Mode, NormalMode, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatestplus.mockito.MockitoSugar
import pages.{InvoiceNumberPage, VrnWarningFlowPage}
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.InvoiceNumberView

import scala.concurrent.Future
import models.CheckMode
import models.SupplierTaxNumber
import pages.{PurchaseTypePage, RefundingCountryPage, SupplierTaxNumberPage}
import models.PurchaseType

class InvoiceNumberControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute = Call("GET", "/foo")

  val formProvider = new InvoiceNumberFormProvider()
  val form = formProvider()

  def invoiceNumberRoute = routes.InvoiceNumberController.onPageLoad(NormalMode).url

  def backLink(mode: Mode): Call = routes.InvoiceTypeController.onPageLoad(mode)

  "InvoiceNumber Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceNumberRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[InvoiceNumberView]

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, backLink(NormalMode))(request, messages(application)).toString
        )
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      val userAnswers = UserAnswers(userAnswersId).set(InvoiceNumberPage, "answer").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceNumberRoute)

        val view = application.injector.instanceOf[InvoiceNumberView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form.fill("answer"), NormalMode, backLink(NormalMode))(
            request,
            messages(application)
          ).toString
        )
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
          FakeRequest(POST, invoiceNumberRoute)
            .withFormUrlEncodedBody(("value", "answer"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must redirect to CYA when in CheckMode and value unchanged" in {

      val userAnswers = emptyUserAnswers.set(InvoiceNumberPage, "INV-1").success.value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .build()

      running(application) {
        val request =
          FakeRequest(POST, routes.InvoiceNumberController.onSubmit(models.CheckMode).url)
            .withFormUrlEncodedBody(("value", "INV-1"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
      }
    }

    "must persist and redirect to CYA when in CheckMode and value changed" in {

      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val userAnswers = emptyUserAnswers.set(InvoiceNumberPage, "INV-1").success.value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, routes.InvoiceNumberController.onSubmit(models.CheckMode).url)
            .withFormUrlEncodedBody(("value", "INV-2"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository).set(captor.capture())
        val saved = captor.getValue
        saved.get(InvoiceNumberPage) mustBe Some("INV-2")
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, invoiceNumberRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[InvoiceNumberView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(boundForm, NormalMode, backLink(NormalMode))(request, messages(application)).toString
        )
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, invoiceNumberRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, invoiceNumberRoute)
            .withFormUrlEncodedBody(("value", "answer"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to warning when invoice unchanged and warning flag set" in {

      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(InvoiceNumberPage, "INV123")
        .success
        .value
        .set(pages.SupplierTaxIdentifierWarningShownPage, true)
        .success
        .value

      val application =
        applicationBuilder(userAnswers = Some(ua))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, invoiceNumberRoute)
            .withFormUrlEncodedBody(("value", "INV123"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.SupplierTaxIdentifierWarningController.onPageLoad(NormalMode).url
      }
    }

    "must route to SupplierTaxIdentifierNumber when invoice changed and warning flag set" in {

      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(InvoiceNumberPage, "INV123")
        .success
        .value
        .set(pages.SupplierTaxIdentifierWarningShownPage, true)
        .success
        .value

      val application =
        applicationBuilder(userAnswers = Some(ua))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, invoiceNumberRoute)
            .withFormUrlEncodedBody(("value", "INV124"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.SupplierTaxIdentifierNumberController.onPageLoad(NormalMode).url
      }
    }

    "must redirect to Supplier VAT reg page when in CheckMode, country Germany and supplier type VAT reg" in {
      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(InvoiceNumberPage, "INV-1")
        .success
        .value
        .set(RefundingCountryPage, "DE")
        .success
        .value
        .set(SupplierTaxNumberPage, SupplierTaxNumber.Vatregistrationnumber)
        .success
        .value
        .set(PurchaseTypePage, Fuel)
        .success
        .value

      val application =
        applicationBuilder(userAnswers = Some(ua))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, routes.InvoiceNumberController.onSubmit(models.CheckMode).url)
            .withFormUrlEncodedBody(("value", "INV-2"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.SupplierVatRegistrationNumberController.onPageLoad(CheckMode).url

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository).set(captor.capture())
        val saved = captor.getValue
        saved.get(InvoiceNumberPage) mustBe Some("INV-2")
      }
    }

    "must redirect to Supplier Tax Identifier page when in CheckMode, country Germany and supplier type Tax identifier" in {
      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(InvoiceNumberPage, "INV-1")
        .success
        .value
        .set(RefundingCountryPage, "DE")
        .success
        .value
        .set(SupplierTaxNumberPage, SupplierTaxNumber.Taxidentifiernumber)
        .success
        .value
        .set(PurchaseTypePage, Fuel)
        .success
        .value

      val application =
        applicationBuilder(userAnswers = Some(ua))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, routes.InvoiceNumberController.onSubmit(models.CheckMode).url)
            .withFormUrlEncodedBody(("value", "INV-2"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.SupplierTaxIdentifierNumberController.onPageLoad(CheckMode).url

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository).set(captor.capture())
        val saved = captor.getValue
        saved.get(InvoiceNumberPage) mustBe Some("INV-2")
      }
    }

    "must route to the warning page when the invoice is unchanged and came from the warning" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val userAnswers = emptyUserAnswers
        .set(InvoiceNumberPage, "SAME-INV")
        .success
        .value
        .set(VrnWarningFlowPage, true)
        .success
        .value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

      running(application) {
        val request = FakeRequest(POST, invoiceNumberRoute).withFormUrlEncodedBody(("value", "SAME-INV"))
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.SupplierVrnWarningController.onPageLoad(NormalMode).url
      }
    }

    "must route to RA8.2 when the invoice is changed and came from the warning" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val userAnswers = emptyUserAnswers
        .set(InvoiceNumberPage, "OLD-INV")
        .success
        .value
        .set(VrnWarningFlowPage, true)
        .success
        .value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

      running(application) {
        val request = FakeRequest(POST, invoiceNumberRoute).withFormUrlEncodedBody(("value", "NEW-INV"))
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.SupplierVatRegistrationNumberController.onPageLoad(NormalMode).url
      }
    }
  }
}
