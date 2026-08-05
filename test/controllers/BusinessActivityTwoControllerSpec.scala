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
import forms.BusinessActivityTwoFormProvider
import models.{CheckMode, NormalMode}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.{BusinessActivityCodePage, BusinessActivityCodeTwoPage, BusinessActivityTwoPage}
import play.api.data.Form
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.BusinessActivityTwoView

import scala.concurrent.Future

class BusinessActivityTwoControllerSpec extends SpecBase with MockitoSugar {
  def onwardRoute: Call = Call("GET", "/foo")

  private val baCode1 = "49200"
  val formProvider = new BusinessActivityTwoFormProvider()
  val form: Form[Boolean] = formProvider()
  private def backLink: Call = routes.BusinessActivityCodeTwoController.onPageLoad(NormalMode)
  lazy val businessActivityTwoRoute: String = routes.BusinessActivityTwoController.onPageLoad(NormalMode).url

  "BusinessActivityTwo Controller" - {

    "must return OK and the correct view for a GET" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, businessActivityTwoRoute)
        val result = route(application, request).value
        val view = application.injector.instanceOf[BusinessActivityTwoView]
        val formProvider = application.injector.instanceOf[BusinessActivityTwoFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLink, baCode1, "48120")(request, messages(application)).toString)
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, businessActivityTwoRoute).withCSRFToken
        val view = application.injector.instanceOf[BusinessActivityTwoView]
        val result = route(application, request).value

        val formProvider = application.injector.instanceOf[BusinessActivityTwoFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLink, baCode1, "48120")(request, messages(application)).toString)
      }
    }

    "must redirect to the next page when valid data is submitted if yes selected" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, true)
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
          FakeRequest(POST, businessActivityTwoRoute)
            .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must redirect to the next page when valid data is submitted if no selected" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, false)
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
          FakeRequest(POST, businessActivityTwoRoute)
            .withFormUrlEncodedBody(("value", "false"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, true)
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, businessActivityTwoRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))
        val view = application.injector.instanceOf[BusinessActivityTwoView]
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include("Select yes if you want to add another SIC code")
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, businessActivityTwoRoute).withCSRFToken
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, businessActivityTwoRoute)
            .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must have the correct back link" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, true)
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, businessActivityTwoRoute).withCSRFToken
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include(
          routes.BusinessActivityCodeTwoController.onPageLoad(NormalMode).url
        )
      }
    }

    "must display error message when no radio button is selected" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, true)
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, businessActivityTwoRoute)
          .withFormUrlEncodedBody(("value", ""))
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include("Select yes if you want to add another SIC code")
      }
    }

    "must display the business activity code in the summary list" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, true)
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, businessActivityTwoRoute).withCSRFToken
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include("Second SIC code")
        contentAsString(result) must include("48120")
      }
    }

    "must display the correct change link for business activity code two" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, true)
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, businessActivityTwoRoute).withCSRFToken
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include("business-activity-2")
      }
    }

    "must display the correct inset text" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, true)
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, businessActivityTwoRoute).withCSRFToken
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include(messages(application)("businessActivityTwo.insetText", baCode1))
        contentAsString(result) must include("49200")
      }
    }

    "must display There is a problem in the error summary when no radio button is selected" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, true)
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, businessActivityTwoRoute)
          .withFormUrlEncodedBody(("value", ""))

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include("There is a problem")
      }
    }

    "must display inline error message above radio buttons when no radio button is selected" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, true)
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, businessActivityTwoRoute)
          .withFormUrlEncodedBody(("value", ""))
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include("Select yes if you want to add another SIC code")
      }
    }

    "must return OK for a GET in Check mode" in {
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, true)
        .success
        .value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.BusinessActivityTwoController.onPageLoad(CheckMode).url)
        val result = route(application, request).value

        status(result) mustEqual OK
      }
    }

    "must redirect to the next page when valid data is submitted in Check mode" in {
      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityCodePage, "49200")
        .success
        .value
        .set(BusinessActivityCodeTwoPage, "48120")
        .success
        .value
        .set(BusinessActivityTwoPage, true)
        .success
        .value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, routes.BusinessActivityTwoController.onSubmit(CheckMode).url)
            .withFormUrlEncodedBody(("value", "true"))
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }
  }
}
