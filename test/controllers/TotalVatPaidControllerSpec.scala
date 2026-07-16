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
import forms.TotalVatPaidFormProvider
import models.{NormalMode, UserAnswers}
import pages.TotalVatPaidPage
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.TotalVatPaidView

import scala.concurrent.Future

class TotalVatPaidControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute = Call("GET", "/foo")

  val formProvider = new TotalVatPaidFormProvider()
  val form = formProvider()

  lazy val url = routes.TotalVatPaidController.onPageLoad(NormalMode).url

  "TotalVatPaid Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, url)

        val result = play.api.test.Helpers.route(application, request).value

        val view = application.injector.instanceOf[TotalVatPaidView]

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode), "€", "Euro")(
          request,
          messages(application)
        ).toString)
      }
    }

    "must pre-fill the form when saved answers exist" in {

      val answers = emptyUserAnswers.set(TotalVatPaidPage, BigDecimal("12.34")).success.value

      val application = applicationBuilder(userAnswers = Some(answers)).build()

      running(application) {
        val request = FakeRequest(GET, url)

        val result = play.api.test.Helpers.route(application, request).value

        val view = application.injector.instanceOf[TotalVatPaidView]

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form.fill(BigDecimal("12.34")),
                       NormalMode,
                       routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode),
                       "€",
                       "Euro"
                      )(request, messages(application)).toString)
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
          FakeRequest(POST, url)
            .withFormUrlEncodedBody(("value", "123.45"))

        val result = play.api.test.Helpers.route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, url)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[TotalVatPaidView]

        val result = play.api.test.Helpers.route(application, request).value

        status(result) mustEqual BAD_REQUEST
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(boundForm,
                                               NormalMode,
                                               routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode),
                                               "€",
                                               "Euro"
                                              )(
          request,
          messages(application)
        ).toString)
      }
    }

    "must redirect to Journey Recovery when no existing data is found on GET" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, url)

        val result = play.api.test.Helpers.route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery when no existing data is found on POST" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, url)
            .withFormUrlEncodedBody(("value", "123.45"))

        val result = play.api.test.Helpers.route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
