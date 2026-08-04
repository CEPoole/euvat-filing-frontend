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
import forms.SupplierVatRegistrationNumberFormProvider
import models.responses.{AddPurchaseResponse, ApplicationResponse, SupplierVrnCountResponse}
import models.{CheckMode, NormalMode, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import models.PurchaseType
import pages.{AddPurchaseResponsePage, ClaimApplicationResponsePage, InvoiceNumberPage, PurchaseTypePage, RefundingCountryPage, RefundingCountryPage, SupplierVatRegistrationNumberPage}
import play.api.data.Form
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.SupplierVatRegistrationNumberView

import scala.concurrent.Future

class SupplierVatRegistrationNumberControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute: Call = Call("GET", "/foo")

  val formProvider = new SupplierVatRegistrationNumberFormProvider()
  val form: Form[String] = formProvider()

  lazy val supplierVatRegistrationNumberRoute: String = routes.SupplierVatRegistrationNumberController.onPageLoad(NormalMode).url

  val seededAnswers = emptyUserAnswers
    .set(ClaimApplicationResponsePage, ApplicationResponse(134, "GB123134", 1)).success.value
    .set(AddPurchaseResponsePage, AddPurchaseResponse(1, 2)).success.value
    .set(InvoiceNumberPage, "INV123").success.value

  "SupplierVatRegistrationNumber Controller" - {
    "must return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, supplierVatRegistrationNumberRoute)
        val result = route(application, request).value
        val view = application.injector.instanceOf[SupplierVatRegistrationNumberView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, routes.SupplierTaxNumberController.onPageLoad(NormalMode), false)(
          request,
          messages(application)
        ).toString
      }
    }

    "must show the Germany-specific hint when the refunding country is Germany" in {
      val userAnswers = emptyUserAnswers.set(RefundingCountryPage, "DE").success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, supplierVatRegistrationNumberRoute)
        val result = route(application, request).value
        val view = application.injector.instanceOf[SupplierVatRegistrationNumberView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, routes.SupplierTaxNumberController.onPageLoad(NormalMode), true)(
          request,
          messages(application)
        ).toString
      }
    }

    "must show the default hint when the refunding country is not Germany" in {
      val userAnswers = emptyUserAnswers.set(RefundingCountryPage, "FR").success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, supplierVatRegistrationNumberRoute)
        val result = route(application, request).value
        val view = application.injector.instanceOf[SupplierVatRegistrationNumberView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, routes.SupplierTaxNumberController.onPageLoad(NormalMode), false)(
          request,
          messages(application)
        ).toString
      }
    }

    "must show the Germany-specific hint regardless of the casing of the country code" in {
      val germanyVariants = Seq("DE", "de", "De", "dE")

      germanyVariants.foreach { countryCode =>
        val userAnswers = emptyUserAnswers.set(RefundingCountryPage, countryCode).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, supplierVatRegistrationNumberRoute)
          val result = route(application, request).value
          val view = application.injector.instanceOf[SupplierVatRegistrationNumberView]

          withClue(s"failed for country code: $countryCode") {
            status(result) mustEqual OK
            contentAsString(result) mustEqual view(form, NormalMode, routes.SupplierTaxNumberController.onPageLoad(NormalMode), true)(
              request,
              messages(application)
            ).toString
          }
        }
      }
    }
    "must populate the view correctly on a GET when the question has previously been answered" in {
      val userAnswers = UserAnswers(userAnswersId).set(SupplierVatRegistrationNumberPage, "answer").success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, supplierVatRegistrationNumberRoute)
        val view = application.injector.instanceOf[SupplierVatRegistrationNumberView]
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form.fill("answer"), NormalMode, routes.SupplierTaxNumberController.onPageLoad(NormalMode), false)(
          request,
          messages(application)
        ).toString
      }
    }

    "must redirect to the next page when no duplicate is found" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
      when(mockEuVatRefundsService.getSupplierVrnCount(any())(any()))
        .thenReturn(Future.successful(SupplierVrnCountResponse(0)))

      val application =
        applicationBuilder(userAnswers = Some(seededAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, supplierVatRegistrationNumberRoute)
            .withFormUrlEncodedBody(("value", "FR123456789"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must redirect to the warning page when a duplicate is found" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
      when(mockEuVatRefundsService.getSupplierVrnCount(any())(any()))
        .thenReturn(Future.successful(SupplierVrnCountResponse(1)))

      val application =
        applicationBuilder(userAnswers = Some(seededAnswers))
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

      running(application) {
        val request =
          FakeRequest(POST, supplierVatRegistrationNumberRoute)
            .withFormUrlEncodedBody(("value", "FR123456789"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.SupplierVrnWarningController.onPageLoad(NormalMode).url
      }
    }

    "must redirect to Journey Recovery when the duplicate check fails" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
      when(mockEuVatRefundsService.getSupplierVrnCount(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("boom")))

      val application =
        applicationBuilder(userAnswers = Some(seededAnswers))
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

      running(application) {
        val request =
          FakeRequest(POST, supplierVatRegistrationNumberRoute)
            .withFormUrlEncodedBody(("value", "FR123456789"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery when required cache data is missing" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

      running(application) {
        val request =
          FakeRequest(POST, supplierVatRegistrationNumberRoute)
            .withFormUrlEncodedBody(("value", "FR123456789"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, supplierVatRegistrationNumberRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))
        val view = application.injector.instanceOf[SupplierVatRegistrationNumberView]
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, NormalMode, routes.SupplierTaxNumberController.onPageLoad(NormalMode), false)(
          request,
          messages(application)
        ).toString
      }
    }

    "must return a Bad Request and errors when more than 12 characters are submitted" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, supplierVatRegistrationNumberRoute)
            .withFormUrlEncodedBody(("value", "a" * 13))

        val boundForm = form.bind(Map("value" -> "a" * 13))
        val view = application.injector.instanceOf[SupplierVatRegistrationNumberView]
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, NormalMode, routes.SupplierTaxNumberController.onPageLoad(NormalMode), false)(
          request,
          messages(application)
        ).toString
      }
    }

    "must return a Bad Request and errors when invalid data is submitted in CheckMode" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, routes.SupplierVatRegistrationNumberController.onSubmit(CheckMode).url)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))
        val view = application.injector.instanceOf[SupplierVatRegistrationNumberView]
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, CheckMode, routes.SupplierTaxNumberController.onPageLoad(CheckMode), false)(
          request,
          messages(application)
        ).toString
      }
    }

    "must redirect to the next page when no duplicate is found in CheckMode" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
      when(mockEuVatRefundsService.getSupplierVrnCount(any())(any()))
        .thenReturn(Future.successful(SupplierVrnCountResponse(0)))

      val application =
        applicationBuilder(userAnswers = Some(seededAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, routes.SupplierVatRegistrationNumberController.onSubmit(CheckMode).url)
            .withFormUrlEncodedBody(("value", "FR123456789"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, supplierVatRegistrationNumberRoute)
        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, supplierVatRegistrationNumberRoute)
            .withFormUrlEncodedBody(("value", "FR123456789"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must persist and redirect to purchase CYA when in CheckMode and part of purchase journey" in {

      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val userAnswers = emptyUserAnswers.set(PurchaseTypePage, PurchaseType.Fuel).success.value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, routes.SupplierVatRegistrationNumberController.onSubmit(CheckMode).url)
            .withFormUrlEncodedBody(("value", "FR123456789"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        org.mockito.Mockito.verify(mockSessionRepository).set(captor.capture())
        val saved = captor.getValue
        saved.get(SupplierVatRegistrationNumberPage) mustBe Some("FR123456789")
      }
    }
  }
}