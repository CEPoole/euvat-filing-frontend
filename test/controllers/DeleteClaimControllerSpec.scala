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
import forms.DeleteClaimFormProvider
import models.{NormalMode, RefundPeriod, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.{DeleteClaimPage, RefundPeriodPage, RefundingCountryNamePage}
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import utils.DateTimeFormats.shortMonthYearFormat
import views.html.DeleteClaimView

import java.time.LocalDateTime
import scala.concurrent.Future

class DeleteClaimControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute = Call("GET", "/foo")

  val formProvider = new DeleteClaimFormProvider()
  val form = formProvider()

  lazy val deleteClaimRoute = routes.DeleteClaimController.onPageLoad().url

  val testRefundPeriod = RefundPeriod(
    startDate = LocalDateTime.of(2025, 4, 1, 0, 0),
    endDate = LocalDateTime.of(2025, 8, 31, 23, 59, 59, 999000000)
  )

  val populatedAnswers = emptyUserAnswers
    .set(RefundingCountryNamePage, "Poland").success.value
    .set(RefundPeriodPage, testRefundPeriod).success.value

  "DeleteClaim Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(populatedAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, deleteClaimRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[DeleteClaimView]
        implicit val msgs = messages(application)
        implicit val lang = msgs.lang

        val expectedMemberState = "Poland"
        val expectedStart = testRefundPeriod.startDate.format(shortMonthYearFormat())
        val expectedEnd = testRefundPeriod.endDate.format(shortMonthYearFormat())

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, expectedMemberState, expectedStart, expectedEnd)(request, msgs).toString
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      val userAnswers = populatedAnswers.set(DeleteClaimPage, true).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, deleteClaimRoute)

        val view = application.injector.instanceOf[DeleteClaimView]
        implicit val msgs = messages(application)
        implicit val lang = msgs.lang

        val expectedMemberState = "Poland"
        val expectedStart = testRefundPeriod.startDate.format(shortMonthYearFormat())
        val expectedEnd = testRefundPeriod.endDate.format(shortMonthYearFormat())

        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form.fill(true), expectedMemberState, expectedStart, expectedEnd)(request, msgs).toString
      }
    }

    "must redirect to the next page when valid data is submitted" in {

      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(populatedAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, deleteClaimRoute)
            .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(populatedAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, deleteClaimRoute)
            .withFormUrlEncodedBody(("value", ""))

        val boundForm = form.bind(Map("value" -> ""))

        val view = application.injector.instanceOf[DeleteClaimView]
        implicit val msgs = messages(application)
        implicit val lang = msgs.lang

        val expectedMemberState = "Poland"
        val expectedStart = testRefundPeriod.startDate.format(shortMonthYearFormat())
        val expectedEnd = testRefundPeriod.endDate.format(shortMonthYearFormat())

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, expectedMemberState, expectedStart, expectedEnd)(request, msgs).toString
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, deleteClaimRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, deleteClaimRoute)
            .withFormUrlEncodedBody(("value", "true"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}