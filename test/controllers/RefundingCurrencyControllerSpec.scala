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
import forms.RefundingCurrencyFormProvider
import models.{NormalMode, RefundingCurrency, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import pages.{RefundingCountryPage, RefundingCurrencyPage}
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.RefundingCurrencyView

import scala.concurrent.Future

class RefundingCurrencyControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute = Call("GET", "/foo")

  lazy val refundingCurrencyRoute = routes.RefundingCurrencyController.onPageLoad(NormalMode).url

  val formProvider = new RefundingCurrencyFormProvider()
  val form = formProvider()

  val userAnswersWithEstonia = emptyUserAnswers.set(RefundingCountryPage, "EE").success.value
  val userAnswersWithCzech = emptyUserAnswers.set(RefundingCountryPage, "CZ").success.value

  "RefundingCurrency Controller" - {

    "must show back link to country when language page is skipped for single-language country" in {

      val application = applicationBuilder(userAnswers = Some(userAnswersWithCzech)).build()

      running(application) {
        val request = FakeRequest(GET, refundingCurrencyRoute)
        val result = route(application, request).value

        status(result) mustEqual OK
        // back link should point to RefundingCountry when language is skipped
        contentAsString(result) must include(routes.RefundingCountryController.onPageLoad(NormalMode).url)
      }
    }

    "must return OK and the correct view for a GET when country is Estonia" in {

      val application = applicationBuilder(userAnswers = Some(userAnswersWithEstonia)).build()

      running(application) {
        val request = FakeRequest(GET, refundingCurrencyRoute)
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include(messages(application)("refundingCurrency.heading"))
        contentAsString(result) must include("Euro (€)")
        contentAsString(result) must include("Estonian Kroon (kr)")
      }
    }

    "must redirect to Journey Recovery for a GET if no country is in session" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, refundingCurrencyRoute)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must return OK on a GET when the question has previously been answered" in {
      val userAnswers = userAnswersWithEstonia.set(RefundingCurrencyPage, "EUR").success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, refundingCurrencyRoute)
        val result = route(application, request).value

        status(result) mustEqual OK
      }
    }

    "must redirect to the next page when valid data is submitted" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(userAnswersWithEstonia))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, refundingCurrencyRoute)
            .withFormUrlEncodedBody(("value", RefundingCurrency.Euro.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(userAnswersWithEstonia)).build()

      running(application) {
        val request =
          FakeRequest(POST, refundingCurrencyRoute)
            .withFormUrlEncodedBody(("value", "invalid value"))

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, refundingCurrencyRoute)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, refundingCurrencyRoute)
            .withFormUrlEncodedBody(("value", RefundingCurrency.Euro.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must resolve country code from delimited RefundingCountryNamePage format on GET" in {
      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryNamePage, "BG,Bulgaria").success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, refundingCurrencyRoute)
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include("Bulgarian Lev (лв)")
      }
    }

    "must redirect to Journey Recovery on POST if form has errors and no country is in session" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, refundingCurrencyRoute)
            .withFormUrlEncodedBody(("value", "invalid value"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must resolve country code from delimited RefundingCountryNamePage format on POST error" in {
      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryNamePage, "BG,Bulgaria").success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, refundingCurrencyRoute)
            .withFormUrlEncodedBody(("value", "invalid value"))

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include("Bulgarian Lev (лв)")
      }
    }

    "must set ClaimDetailsAmendedPage to true when currency is changed and ClaimDetailsCompletedPage is true" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(pages.RefundingCountryPage, "EE")
        .success
        .value
        .set(pages.RefundingCurrencyPage, "EEK")
        .success
        .value
        .set(pages.ClaimDetailsCompletedPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCurrencyController.onSubmit(models.CheckMode).url)
          .withFormUrlEncodedBody(("value", "euro"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        captor.getValue.get(pages.ClaimDetailsAmendedPage) mustBe Some(true)
      }
    }

    "must NOT set ClaimDetailsAmendedPage when currency is unchanged" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(pages.RefundingCountryPage, "EE")
        .success
        .value
        .set(pages.RefundingCurrencyPage, "EUR")
        .success
        .value
        .set(pages.ClaimDetailsCompletedPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCurrencyController.onSubmit(models.CheckMode).url)
          .withFormUrlEncodedBody(("value", "euro"))

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
        .set(pages.RefundingCountryPage, "EE")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCurrencyController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "euro"))

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
