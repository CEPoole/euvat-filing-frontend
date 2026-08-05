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
import forms.SimplifiedInvoiceVatRegCheckFormProvider
import models.{NormalMode, SupplierAddress, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.{SimplifiedInvoiceVatRegCheckPage, SupplierAddressPage}
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.SimplifiedInvoiceVatRegCheckView

import scala.concurrent.Future

class SimplifiedInvoiceVatRegCheckControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute = Call("GET", "/foo")

  val formProvider = new SimplifiedInvoiceVatRegCheckFormProvider()
  val form = formProvider()

  lazy val simplifiedInvoiceVatRegCheckRoute: String = routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(NormalMode).url
  private lazy val backLink: Call = routes.SupplierAddressController.onPageLoad(NormalMode)

  val userAnswersWithAddress: UserAnswers = emptyUserAnswers
    .set(SupplierAddressPage, SupplierAddress("1 High Street", None, None))
    .success
    .value

  "SimplifiedInvoiceVatRegCheck Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(userAnswersWithAddress)).build()

      running(application) {
        val request = FakeRequest(GET, simplifiedInvoiceVatRegCheckRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[SimplifiedInvoiceVatRegCheckView]

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLink)(request, messages(application)).toString)
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      val userAnswers = userAnswersWithAddress.set(SimplifiedInvoiceVatRegCheckPage, true).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, simplifiedInvoiceVatRegCheckRoute)

        val view = application.injector.instanceOf[SimplifiedInvoiceVatRegCheckView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form.fill(true), NormalMode, backLink)(request, messages(application)).toString)
      }
    }

    "must redirect to Journey Recovery for a GET if no supplier address data is found" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, simplifiedInvoiceVatRegCheckRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must load the page for a GET if supplier address data is found" in {

      val application = applicationBuilder(userAnswers = Some(userAnswersWithAddress)).build()

      running(application) {
        val request = FakeRequest(GET, simplifiedInvoiceVatRegCheckRoute)

        val result = route(application, request).value

        status(result) mustEqual OK
      }
    }

    "must redirect to the correct page when the user selects Yes" in {

      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(userAnswersWithAddress))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, simplifiedInvoiceVatRegCheckRoute)
            .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.SupplierVatRegistrationNumberController.onPageLoad(NormalMode).url
      }
    }

    "must redirect to the correct page when the user selects No" in {

      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(userAnswersWithAddress))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, simplifiedInvoiceVatRegCheckRoute)
            .withFormUrlEncodedBody(("value", "false"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.PurchaseTypeController.onPageLoad(NormalMode).url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(userAnswersWithAddress)).build()

      running(application) {
        val request =
          FakeRequest(POST, simplifiedInvoiceVatRegCheckRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[SimplifiedInvoiceVatRegCheckView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(boundForm, NormalMode, backLink)(request, messages(application)).toString)
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, simplifiedInvoiceVatRegCheckRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, simplifiedInvoiceVatRegCheckRoute)
            .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
