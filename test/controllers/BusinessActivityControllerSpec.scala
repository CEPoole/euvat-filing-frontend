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
import forms.BusinessActivityFormProvider
import models.{NormalMode, UserAnswers}
import models.responses.TraderKnownFactsResponse
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pages.BusinessActivityPage
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import queries.TraderKnownFactsQuery
import repositories.SessionRepository
import services.EuVatRefundsService
import uk.gov.hmrc.http.HeaderCarrier
import views.html.BusinessActivityView

import scala.concurrent.Future

class BusinessActivityControllerSpec extends SpecBase with MockitoSugar with ScalaFutures {
  private val onwardRoute = Call("GET", "/foo")
  private lazy val pageLoadRoute = routes.BusinessActivityController.onPageLoad(NormalMode).url
  private lazy val submitRoute = routes.BusinessActivityController.onSubmit(NormalMode).url
  private lazy val backLink: Call = routes.ContactDetailsController.onPageLoad(NormalMode)
  private val baCode1 = "49200"

  "BusinessActivity Controller" - {

    "must return OK and the correct view for a GET" in {
      val userAnswers = emptyUserAnswers
        .set(TraderKnownFactsQuery, TraderKnownFactsResponse(123, tradeClass = Some(baCode1)))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .build()

      running(application) {
        val request = FakeRequest(GET, pageLoadRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[BusinessActivityView]
        val formProvider = application.injector.instanceOf[BusinessActivityFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLink, baCode1)(request, messages(application)).toString)
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
      val userAnswers = emptyUserAnswers
        .set(BusinessActivityPage, true)
        .success
        .value
        .set(TraderKnownFactsQuery, TraderKnownFactsResponse(123, tradeClass = Some(baCode1)))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .build()

      running(application) {
        val request = FakeRequest(GET, pageLoadRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[BusinessActivityView]
        val formProvider = application.injector.instanceOf[BusinessActivityFormProvider]
        val form = formProvider().fill(true)

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLink, baCode1)(request, messages(application)).toString)
      }
    }

    "must redirect to the next page and persist the answer when valid data is submitted (Yes)" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, submitRoute)
          .withFormUrlEncodedBody("value" -> true.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
        verify(mockSessionRepository, times(1)).set(any())
      }
    }

    "must redirect to the next page and persist the answer when valid data is submitted (No)" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, submitRoute)
          .withFormUrlEncodedBody("value" -> "false")
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
        verify(mockSessionRepository, times(1)).set(any())
      }
    }

    "must return Bad Request when radio option not selected" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .build()

      running(application) {
        val request = FakeRequest(POST, submitRoute).withFormUrlEncodedBody("value" -> "")
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include(messages(application)("businessActivity.error.required"))
      }
    }

    "must redirect to Journey Recovery when no existing data is found on POST" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(POST, submitRoute)
          .withFormUrlEncodedBody("value" -> true.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must set ClaimDetailsAmendedPage to true when business activity is changed and ClaimDetailsCompletedPage is true" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(pages.BusinessActivityPage, false)
        .success
        .value
        .set(pages.ClaimDetailsCompletedPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.BusinessActivityController.onSubmit(models.CheckMode).url)
          .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        captor.getValue.get(pages.ClaimDetailsAmendedPage) mustBe Some(true)
      }
    }

    "must NOT set ClaimDetailsAmendedPage when business activity is unchanged" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(pages.BusinessActivityPage, true)
        .success
        .value
        .set(pages.ClaimDetailsCompletedPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.BusinessActivityController.onSubmit(models.CheckMode).url)
          .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        captor.getValue.get(pages.ClaimDetailsAmendedPage).isDefined mustBe false
      }
    }

    "must NOT set ClaimDetailsAmendedPage when ClaimDetailsCompletedPage is not set" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.BusinessActivityController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        captor.getValue.get(pages.ClaimDetailsAmendedPage).isDefined mustBe false
      }
    }
  }
}
