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
import controllers.actions.{DataRequiredAction, DataRetrievalAction, IdentifierAction}
import models.{CheckMode, NormalMode, RefundPeriod}
import pages.RefundPeriodPage
import play.api.i18n.MessagesApi
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import views.html.ConfirmRefundPeriodStartDateView

import java.time.{LocalDate, LocalDateTime, YearMonth}
import scala.language.postfixOps

class ConfirmRefundPeriodStartDateControllerSpec extends SpecBase {

  private def controllerWithFixedToday(fixedToday: LocalDate)(implicit application: play.api.Application): ConfirmRefundPeriodStartDateController =
    new ConfirmRefundPeriodStartDateController(
      messagesApi          = application.injector.instanceOf[MessagesApi],
      identify             = application.injector.instanceOf[IdentifierAction],
      getData              = application.injector.instanceOf[DataRetrievalAction],
      requireData          = application.injector.instanceOf[DataRequiredAction],
      controllerComponents = application.injector.instanceOf[MessagesControllerComponents],
      view                 = application.injector.instanceOf[ConfirmRefundPeriodStartDateView]
    ) {
      override protected def today: LocalDate = fixedToday
    }

  "ConfirmRefundPeriodStartDate Controller" - {

    "must return OK and the correct view for a GET" in {

      val refundPeriod = RefundPeriod(
        startDate = LocalDateTime.of(2024, 10, 1, 0, 0),
        endDate   = LocalDateTime.of(2024, 12, 31, 23, 59)
      )

      val userAnswers = emptyUserAnswers.set(RefundPeriodPage, refundPeriod).success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.ConfirmRefundPeriodStartDateController.onPageLoad(NormalMode).url)
        val result = route(application, request).value
        val view = application.injector.instanceOf[ConfirmRefundPeriodStartDateView]

        val expectedStartDate = "10/2024"
        val expectedMinDate = "01/2025" // correct as of today's real date
        val expectedCall = routes.RefundPeriodController.onPageLoad(NormalMode)

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(expectedStartDate, expectedMinDate, expectedCall, NormalMode)(request, messages(application)).toString
      }
    }

    "must return OK and the correct view for a GET in CheckMode" in {

      val refundPeriod = RefundPeriod(
        startDate = LocalDateTime.of(2024, 10, 1, 0, 0),
        endDate   = LocalDateTime.of(2024, 12, 31, 23, 59)
      )

      val userAnswers = emptyUserAnswers.set(RefundPeriodPage, refundPeriod).success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.ConfirmRefundPeriodStartDateController.onPageLoad(CheckMode).url)
        val result = route(application, request).value
        val view = application.injector.instanceOf[ConfirmRefundPeriodStartDateView]

        val expectedStartDate = "10/2024"
        val expectedMinDate = "01/2025"
        val expectedCall = routes.RefundPeriodController.onPageLoad(CheckMode)

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(expectedStartDate, expectedMinDate, expectedCall, CheckMode)(request, messages(application)).toString
      }
    }

    " must redirect to JourneyRecoveryController for a GET in NormalMode if no refund period data exists" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.ConfirmRefundPeriodStartDateController.onPageLoad(NormalMode).url)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    " must redirect to JourneyRecoveryController for a GET in CheckMode if no refund period data exists" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.ConfirmRefundPeriodStartDateController.onPageLoad(CheckMode).url)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    " must redirect to ContactDetailsController on Submit in NormalMode" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.ConfirmRefundPeriodStartDateController.onSubmit(NormalMode).url)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.ContactDetailsController.onPageLoad(NormalMode).url
      }
    }

    " must redirect to CheckYourClaimDetailsController on Submit in CheckMode" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.ConfirmRefundPeriodStartDateController.onSubmit(CheckMode).url)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.CheckYourClaimDetailsController.onPageLoad().url
      }
    }

    "earliestPermittedStartDate must return January of the previous year if today is on or before 30 September" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        implicit val app: play.api.Application = application
        controllerWithFixedToday(LocalDate.of(2029, 9, 30)).earliestPermittedStartDate() mustEqual YearMonth.of(2028, 1)
      }
    }

    "earliestPermittedStartDate must return January of the previous year if today is after 30 September" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        implicit val app: play.api.Application = application
        controllerWithFixedToday(LocalDate.of(2029, 10, 1)).earliestPermittedStartDate() mustEqual YearMonth.of(2029, 1)
      }
    }

    "earliestPermittedStartDate must return January of the previous year exactly on the 30 September boundary" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        implicit val app: play.api.Application = application
        controllerWithFixedToday(LocalDate.of(2030, 9, 30)).earliestPermittedStartDate() mustEqual YearMonth.of(2029, 1)
      }
    }

  }
}
