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
import forms.SuppliersNameFormProvider
import models.{NormalMode, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.SuppliersNamePage
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.SuppliersNameView

import scala.concurrent.Future

class SuppliersNameControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute = Call("GET", "/foo")

  val formProvider = new SuppliersNameFormProvider()
  val form = formProvider()

  lazy val suppliersNameRoute = routes.SuppliersNameController.onPageLoad(NormalMode).url

  "SuppliersName Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, suppliersNameRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[SuppliersNameView]

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, routes.InvoiceDateController.onPageLoad(NormalMode))(request,
                                        messages(application)
                                       ).toString)
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      val userAnswers = UserAnswers(userAnswersId).set(SuppliersNamePage, "answer").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, suppliersNameRoute)

        val view = application.injector.instanceOf[SuppliersNameView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form.fill("answer"), NormalMode, routes.InvoiceDateController.onPageLoad(NormalMode))(
          request,
          messages(application)
        ).toString)
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
          FakeRequest(POST, suppliersNameRoute)
            .withFormUrlEncodedBody(("value", "answer"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, suppliersNameRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[SuppliersNameView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(boundForm, NormalMode, routes.InvoiceDateController.onPageLoad(NormalMode))(request,
                                       messages(application)
                                      ).toString)
      }
    }

    "must return a Bad Request and errors when more than 35 characters are submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, suppliersNameRoute)
            .withFormUrlEncodedBody(("value", "a" * 36))

        val boundForm = form.bind(Map("value" -> "a" * 36))

        val view = application.injector.instanceOf[SuppliersNameView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(boundForm, NormalMode, routes.InvoiceDateController.onPageLoad(NormalMode))(request,
                                       messages(application)
                                      ).toString)
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, suppliersNameRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, suppliersNameRoute)
            .withFormUrlEncodedBody(("value", "answer"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
