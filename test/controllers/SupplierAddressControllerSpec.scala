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
import forms.SupplierAddressFormProvider
import models.{NormalMode, SupplierAddress}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import pages.SupplierAddressPage
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.SupplierAddressView

import scala.concurrent.Future

class SupplierAddressControllerSpec extends SpecBase with MockitoSugar {

  private val onwardRoute = Call("GET", "/foo")

  private lazy val pageLoadRoute = routes.SupplierAddressController.onPageLoad(NormalMode).url
  private lazy val submitRoute = routes.SupplierAddressController.onSubmit(NormalMode).url
  private lazy val backLink: Call = routes.SuppliersNameController.onPageLoad(NormalMode)

  private val validFormData = Map(
    "addressLine1" -> "1 High Street",
    "addressLine2" -> "Apartment 3",
    "addressLine3" -> "London"
  )

  private val supplierAddress = SupplierAddress(
    line1 = "1 High Street",
    line2 = Some("Apartment 3"),
    line3 = Some("London")
  )

  "SupplierAddress Controller" - {

    "must return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, pageLoadRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[SupplierAddressView]
        val formProvider = application.injector.instanceOf[SupplierAddressFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLink)(request, messages(application)).toString)
      }
    }

    "must redirect to Journey Recovery when no existing data is found on GET" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, pageLoadRoute)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must populate the view with the saved value on GET" in {
      val userAnswers = emptyUserAnswers.set(SupplierAddressPage, supplierAddress).success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, pageLoadRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[SupplierAddressView]
        val formProvider = application.injector.instanceOf[SupplierAddressFormProvider]
        val form = formProvider().fill(supplierAddress)

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLink)(request, messages(application)).toString)
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
        val request = FakeRequest(POST, submitRoute).withFormUrlEncodedBody(validFormData.toSeq*)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
        verify(mockSessionRepository, times(1)).set(any())
      }
    }

    "must redirect to the next page when only the mandatory line1 is submitted" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, submitRoute).withFormUrlEncodedBody("addressLine1" -> "1 High Street")
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
        verify(mockSessionRepository, times(1)).set(any())
      }
    }

    "must return Bad Request and the required error when line1 is missing" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, submitRoute).withFormUrlEncodedBody("addressLine1" -> "")
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include(messages(application)("supplierAddress.error.line1.required"))
      }
    }

    "must return Bad Request and the max-length error when line1 is longer than 35 characters" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val tooLong = "a" * 36
        val request = FakeRequest(POST, submitRoute).withFormUrlEncodedBody("addressLine1" -> tooLong)
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include(
          messages(application)(
            "supplierAddress.error.maxLength.withLabel",
            messages(application)("supplierAddress.line1.label"),
            messages(application)("supplierAddress.error.maxLength")
          )
        )
      }
    }

    "must redirect to Journey Recovery when no existing data is found on POST" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(POST, submitRoute).withFormUrlEncodedBody(validFormData.toSeq*)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
