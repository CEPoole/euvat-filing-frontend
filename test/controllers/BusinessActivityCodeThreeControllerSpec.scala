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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import navigation.FakeNavigator
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.inject.bind
import pages.BusinessActivityCodeThreePage
import play.api.mvc.Call

import scala.concurrent.Future

class BusinessActivityCodeThreeControllerSpec extends SpecBase with MockitoSugar {

  val onwardRoute = Call("GET", "/foo")

  "BusinessActivityCodeThree Controller" - {

    // exclusion of prior codes is not required; list should always include all activities

    "must return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.BusinessActivityCodeThreeController.onPageLoad(models.NormalMode).url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[views.html.BusinessActivityCodeThreeView]
        val formProvider = application.injector.instanceOf[forms.BusinessActivityCodeThreeFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, Some(routes.BusinessActivityTwoController.onPageLoad(models.NormalMode).url), models.NormalMode)(
          request,
          messages(application)
        ).toString
      }
    }

    "must redirect to Journey Recovery when no existing data is found" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, routes.BusinessActivityCodeThreeController.onPageLoad(models.NormalMode).url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to next page when valid data is submitted" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.BusinessActivityCodeThreeController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "2534"))
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url

        verify(mockSessionRepository, times(1)).set(any())
      }
    }

    "must pre-fill the form when a saved value exists" in {
      val userAnswers = emptyUserAnswers.set(BusinessActivityCodeThreePage, "2534").success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.BusinessActivityCodeThreeController.onPageLoad(models.NormalMode).url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[views.html.BusinessActivityCodeThreeView]
        val formProvider = application.injector.instanceOf[forms.BusinessActivityCodeThreeFormProvider]
        val form = formProvider().fill("2534")

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, Some(routes.BusinessActivityTwoController.onPageLoad(models.NormalMode).url), models.NormalMode)(
          request,
          messages(application)
        ).toString
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.BusinessActivityCodeThreeController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", ""))
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST

        val body = contentAsString(result)
        body must include(messages(application)("businessActivityCodeThree.error.required"))
        body must include(messages(application)("businessActivityCodeThree.error.summary"))

        val typedRequest = FakeRequest(POST, routes.BusinessActivityCodeThreeController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", ""), ("valueTyped", "NotACode"))

        val typedResult = route(application, typedRequest).value
        status(typedResult) mustEqual BAD_REQUEST
        val typedBody = contentAsString(typedResult)
        typedBody must include(messages(application)("businessActivityCodeThree.error.invalid"))
        typedBody must include(messages(application)("businessActivityCodeThree.error.invalid.summary"))

        val tooLongRequest = FakeRequest(POST, routes.BusinessActivityCodeThreeController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "123456"))

        val tooLongResult = route(application, tooLongRequest).value
        status(tooLongResult) mustEqual BAD_REQUEST
        val tooLongBody = contentAsString(tooLongResult)
        tooLongBody must include(messages(application)("businessActivityCodeThree.error.length"))
      }
    }

    "must return a Bad Request and duplicate error when submitted code matches second business activity" in {
      val userAnswers = emptyUserAnswers
        .set(pages.BusinessActivityCodePage, "4920")
        .flatMap(_.set(pages.BusinessActivityCodeTwoPage, "2534"))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.BusinessActivityCodeThreeController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "2534"))
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        val body = contentAsString(result)

        body must include(messages(application)("businessActivityCodeThree.error.duplicate"))
        body must include("2534")
      }
    }

    "must return a Bad Request and duplicate error when submitted code matches first business activity" in {
      val userAnswers = emptyUserAnswers
        .set(pages.BusinessActivityCodePage, "4920")
        .flatMap(_.set(pages.BusinessActivityCodeTwoPage, "2534"))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.BusinessActivityCodeThreeController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "4920"))
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        val body = contentAsString(result)
        body must include(messages(application)("businessActivityCodeThree.error.duplicate"))
        body must include("4920")
      }
    }

    "must set ClaimDetailsAmendedPage to true when third SIC code is changed and ClaimDetailsCompletedPage is true" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(pages.BusinessActivityCodePage, "7020")
        .success
        .value
        .set(pages.BusinessActivityCodeTwoPage, "1234")
        .success
        .value
        .set(pages.BusinessActivityCodeThreePage, "5678")
        .success
        .value
        .set(pages.ClaimDetailsCompletedPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.BusinessActivityCodeThreeController.onSubmit(models.CheckMode).url)
          .withFormUrlEncodedBody(("value", "9999"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        captor.getValue.get(pages.ClaimDetailsAmendedPage) mustBe Some(true)
      }
    }

    "must NOT set ClaimDetailsAmendedPage when third SIC code is unchanged" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(pages.BusinessActivityCodePage, "7020")
        .success
        .value
        .set(pages.BusinessActivityCodeTwoPage, "1234")
        .success
        .value
        .set(pages.BusinessActivityCodeThreePage, "5678")
        .success
        .value
        .set(pages.ClaimDetailsCompletedPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.BusinessActivityCodeThreeController.onSubmit(models.CheckMode).url)
          .withFormUrlEncodedBody(("value", "5678"))

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

      val ua = emptyUserAnswers
        .set(pages.BusinessActivityCodePage, "7020")
        .success
        .value
        .set(pages.BusinessActivityCodeTwoPage, "1234")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.BusinessActivityCodeThreeController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "5678"))

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
