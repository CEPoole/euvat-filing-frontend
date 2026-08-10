package controllers

import base.SpecBase
import models.{CheckMode, NormalMode, RefundPeriod}
import pages.RefundPeriodPage
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import views.html.ConfirmRefundPeriodStartDateView

import java.time.{LocalDate, LocalDateTime, YearMonth}

class ConfirmRefundPeriodStartDateControllerSpec extends SpecBase {

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

    
  }
}
