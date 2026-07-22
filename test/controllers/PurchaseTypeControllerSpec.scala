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
import forms.PurchaseTypeFormProvider
import models.{CheckMode, NormalMode, PurchaseType}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import pages.PurchaseTypePage
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.PurchaseTypeView

import scala.concurrent.Future

class PurchaseTypeControllerSpec extends SpecBase with MockitoSugar {

  val onwardRoute: Call = Call("GET", "/foo")

  lazy val purchaseTypeRoute: String = routes.PurchaseTypeController.onPageLoad(NormalMode).url
  lazy val purchaseTypeSubmitRoute: String = routes.PurchaseTypeController.onSubmit(NormalMode).url
  lazy val backLinkCall: Call = routes.BeforeYouStartPurchaseController.onPageLoad()

  lazy val purchaseTypeRouteCheck: String = routes.PurchaseTypeController.onPageLoad(CheckMode).url
  lazy val purchaseTypeSubmitRouteCheck: String = routes.PurchaseTypeController.onSubmit(CheckMode).url
  lazy val backLinkCallCheck: Call = routes.BeforeYouStartPurchaseController.onPageLoad()

  "PurchaseType Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, backLinkCall)(request, messages(application)).toString
      }
    }

    "must return OK and the correct view for a GET when simplified invoice check exists with back link to simplified check" in {

      val userAnswers = emptyUserAnswers.set(pages.SimplifiedInvoiceVatRegCheckPage, false).success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, routes.BeforeYouStartPurchaseController.onPageLoad())(
          request,
          messages(application)
        ).toString
      }
    }

      "must return OK and the correct view for a GET when country is Germany with back link to SupplierTaxIdentifierNumber" in {

        val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, purchaseTypeRoute)
          val result = route(application, request).value

          val view = application.injector.instanceOf[PurchaseTypeView]
          val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
          val form = formProvider()

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(form, NormalMode, backLinkCall)(request, messages(application)).toString
        }
      }

    "must return OK and the correct view for a GET when simplified invoice check exists with value Yes and back link to TotalVatPaid" in {

      val userAnswers = emptyUserAnswers.set(pages.SimplifiedInvoiceVatRegCheckPage, true).success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, backLinkCall)(request, messages(application)).toString
      }
    }

    "must return OK and the correct view for a GET in CheckMode with correct back link" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRouteCheck)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, CheckMode, backLinkCallCheck)(request, messages(application)).toString
      }
    }

      "must return OK and the correct view for a GET in CheckMode when country is Germany with back link to SupplierTaxIdentifierNumber" in {

        val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, purchaseTypeRouteCheck)
          val result = route(application, request).value

          val view = application.injector.instanceOf[PurchaseTypeView]
          val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
          val form = formProvider()

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(form, CheckMode, backLinkCallCheck)(request, messages(application)).toString
        }
      }

    "must redirect to Journey Recovery when no existing data is found on GET" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must populate the view with the saved value on GET" in {

      val userAnswers = emptyUserAnswers.set(PurchaseTypePage, PurchaseType.Fuel).success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider().fill(PurchaseType.Fuel)

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, backLinkCall)(request, messages(application)).toString
      }
    }

    "must redirect to the next page and persist the answer when valid data is submitted" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRoute)
          .withFormUrlEncodedBody("value" -> PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
        verify(mockSessionRepository, times(1)).set(any())
      }
    }

    "must return Bad Request and errors when no value is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRoute)
          .withFormUrlEncodedBody("value" -> "")

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include(messages(application)("purchaseType.error.required"))
      }
    }

    "must return Bad Request and errors when an invalid value is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRoute)
          .withFormUrlEncodedBody("value" -> "notARealOption")

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include(messages(application)("purchaseType.error.required"))
      }
    }

    "must redirect to Journey Recovery when no existing data is found on POST" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRoute)
          .withFormUrlEncodedBody("value" -> PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
