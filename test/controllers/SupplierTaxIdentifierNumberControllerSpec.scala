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
import forms.SupplierTaxIdentifierNumberFormProvider
import models.{CheckMode, NormalMode, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.SupplierTaxIdentifierNumberPage
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.SupplierTaxIdentifierNumberView

import scala.concurrent.Future

class SupplierTaxIdentifierNumberControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute = Call("GET", "/foo")

  val formProvider = new SupplierTaxIdentifierNumberFormProvider()
  val form = formProvider()

  lazy val supplierTaxIdentifierNumberRoute = routes.SupplierTaxIdentifierNumberController.onPageLoad(NormalMode).url

  "SupplierTaxIdentifierNumber Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, supplierTaxIdentifierNumberRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[SupplierTaxIdentifierNumberView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, routes.SupplierTaxNumberController.onPageLoad(NormalMode))(
          request,
          messages(application)
        ).toString
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      val userAnswers = UserAnswers(userAnswersId).set(SupplierTaxIdentifierNumberPage, "answer").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, supplierTaxIdentifierNumberRoute)

        val view = application.injector.instanceOf[SupplierTaxIdentifierNumberView]

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form.fill("answer"), NormalMode, routes.SupplierTaxNumberController.onPageLoad(NormalMode))(
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
          FakeRequest(POST, supplierTaxIdentifierNumberRoute)
            .withFormUrlEncodedBody(("value", "1234567890"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, supplierTaxIdentifierNumberRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[SupplierTaxIdentifierNumberView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, NormalMode, routes.SupplierTaxNumberController.onPageLoad(NormalMode))(
          request,
          messages(application)
        ).toString
      }
    }

    "must return a Bad Request and errors when more than 20 characters are submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, supplierTaxIdentifierNumberRoute)
            .withFormUrlEncodedBody(("value", "a" * 21))

        val boundForm = form.bind(Map("value" -> "a" * 21))

        val view = application.injector.instanceOf[SupplierTaxIdentifierNumberView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, NormalMode, routes.SupplierTaxNumberController.onPageLoad(NormalMode))(
          request,
          messages(application)
        ).toString
      }
    }

    "must return a Bad Request and errors when invalid data is submitted in CheckMode" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, routes.SupplierTaxIdentifierNumberController.onSubmit(CheckMode).url)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[SupplierTaxIdentifierNumberView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, CheckMode, routes.SupplierTaxNumberController.onPageLoad(CheckMode))(
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
          FakeRequest(POST, routes.SupplierTaxIdentifierNumberController.onSubmit(CheckMode).url)
            .withFormUrlEncodedBody(("value", "1234567890"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

      "must return OK and the correct view for a GET in CheckMode" in {

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, routes.SupplierTaxIdentifierNumberController.onPageLoad(CheckMode).url)

          val result = route(application, request).value

          val view = application.injector.instanceOf[SupplierTaxIdentifierNumberView]

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(form, CheckMode, routes.SupplierTaxNumberController.onPageLoad(CheckMode))(
            request,
            messages(application)
          ).toString
        }
      }

      "must populate the view correctly on a GET in CheckMode when the question has previously been answered" in {

        val userAnswers = UserAnswers(userAnswersId).set(SupplierTaxIdentifierNumberPage, "answer").success.value

        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, routes.SupplierTaxIdentifierNumberController.onPageLoad(CheckMode).url)

          val view = application.injector.instanceOf[SupplierTaxIdentifierNumberView]

          val result = route(application, request).value

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(form.fill("answer"), CheckMode, routes.SupplierTaxNumberController.onPageLoad(CheckMode))(
            request,
            messages(application)
          ).toString
        }
      }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, supplierTaxIdentifierNumberRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, supplierTaxIdentifierNumberRoute)
            .withFormUrlEncodedBody(("value", "1234567890"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
