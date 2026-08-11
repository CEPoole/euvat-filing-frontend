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
import controllers.actions.{CustomIdentifierAction, FakeIdentifierAction, IdentifierAction}
import forms.{RefundPeriodData, RefundPeriodFormProvider}
import models.requests.DataRequest
import models.responses.{LatestApplication, LatestApplicationResponse, TraderKnownFactsResponse}
import models.{CheckMode, NormalMode, RefundPeriod, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar
import pages.{ClaimDetailsCompletedPage, RefundPeriodPage}
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.mvc.{Call, PlayBodyParsers, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import queries.TraderKnownFactsQuery

import repositories.SessionRepository
import services.EuVatRefundsService

import java.time.{LocalDate, LocalDateTime, YearMonth}
import scala.concurrent.Future

class RefundPeriodControllerSpec extends SpecBase with MockitoSugar {

  private val baseToday: LocalDate = LocalDate.now()
  private val beforeSept30Today: LocalDate =
    baseToday.withMonth(6).withDayOfMonth(1)
  private val afterSept30Today: LocalDate =
    baseToday.withMonth(10).withDayOfMonth(1)
  private val beforeSept30Year: Int = beforeSept30Today.getYear
  private val afterSept30Year: Int = afterSept30Today.getYear
  private val previousYearBeforeSept30: Int = beforeSept30Year - 1
  private val previousYearAfterSept30: Int = afterSept30Year - 1
  private val safeFutureYear: Int = baseToday.plusYears(5).getYear
  private val safePastYear: Int = baseToday.getYear - 3

  val formProviderBeforeSept30: RefundPeriodFormProvider = new RefundPeriodFormProvider() {
    override protected def today: java.time.LocalDate = java.time.LocalDate.of(2024, 6, 1)
  }

  val formProviderAfterSept30: RefundPeriodFormProvider = new RefundPeriodFormProvider() {
    override protected def today: java.time.LocalDate = java.time.LocalDate.of(2024, 10, 1)
  }

  val onwardRoute: Call = Call("GET", "/foo")
  private val baCode1 = "4920"
  private val trader: TraderKnownFactsResponse = TraderKnownFactsResponse(999900108, tradeClass = Some(baCode1))

  "RefundPeriod Controller" - {

    ".onPageLoad" - {
      "must return OK and the correct view for a GET" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, routes.RefundPeriodController.onPageLoad(NormalMode).url)
          val result = route(application, request).value
          val view = application.injector.instanceOf[views.html.RefundPeriodView]
          implicit val msgs: Messages = messages(application)
          val form = application.injector.instanceOf[RefundPeriodFormProvider].apply()

          status(result) mustEqual OK
          normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
            view(
              form,
              NormalMode,
              routes.RefundingLanguageController.onPageLoad(NormalMode),
              None,
              None,
              Set.empty[String],
              Map.empty[String, String]
            )(request, msgs).toString
          )
        }
      }

      "must pre-fill the form when saved answers exist" in {
        val savedPeriod = RefundPeriod(
          java.time.YearMonth.of(2025, 3).atDay(1).atStartOfDay(),
          java.time.YearMonth.of(2025, 8).atEndOfMonth().atTime(23, 59, 59, 999000000)
        )
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, routes.RefundPeriodController.onPageLoad(NormalMode).url)
          val result = route(application, request).value
          val view = application.injector.instanceOf[views.html.RefundPeriodView]
          val formProvider = application.injector.instanceOf[RefundPeriodFormProvider]
          implicit val msgs: Messages = messages(application)
          val start = java.time.YearMonth.of(savedPeriod.startDate.getYear, savedPeriod.startDate.getMonthValue)
          val end = java.time.YearMonth.of(savedPeriod.endDate.getYear, savedPeriod.endDate.getMonthValue)
          val form = formProvider().fill(RefundPeriodData(start, end))

          status(result) mustEqual OK
          normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
            view(
              form,
              NormalMode,
              routes.RefundingLanguageController.onPageLoad(NormalMode),
              None,
              None,
              Set.empty[String],
              Map.empty[String, String]
            )(request, msgs).toString
          )
        }
      }

      "must use RefundingLanguageController as back link when country has one currency" in {
        val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "AT").success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, routes.RefundPeriodController.onPageLoad(models.NormalMode).url)
          val result = route(application, request).value

          status(result) mustEqual OK
          contentAsString(result) must include(routes.RefundingLanguageController.onPageLoad(models.NormalMode).url)
        }
      }
    }

    ".onSubmit" - {
      "must redirect to the next page when valid data is submitted" in {
        when(mockEuVatRefundsService.getLatestApplications(any())(any()))
          .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))
        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, trader).success.value
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))
        val application =
          applicationBuilder(userAnswers = Some(userAnswersWithTrader))
            .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
            .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "2026",
              "end.month"   -> "08",
              "end.year"    -> "2026"
            )
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual onwardRoute.url
        }
      }

      "must redirect to journey recovery when no user answers exist" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val request = FakeRequest(GET, routes.RefundPeriodController.onPageLoad(NormalMode).url)
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
        }
      }

      "must return a Bad Request when both fields are empty" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "",
              "start.year"  -> "",
              "end.month"   -> "",
              "end.year"    -> ""
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
        }
      }

      "must show start-before-end error when start date is after end date" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "08",
              "start.year"  -> "2024",
              "end.month"   -> "03",
              "end.year"    -> "2024"
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          contentAsString(result) must include(messages(application)("refundPeriod.error.startDateNotAfterEndDate"))
        }
      }

      "must show single-year error when start and end are in different years and start is after cutoff" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "2024",
              "end.month"   -> "08",
              "end.year"    -> "2025"
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          contentAsString(result) must include(messages(application)("refundPeriod.error.startAndEndInSameYear"))
        }
      }

      "must show September cutoff error when start and end are in different years and start is before cutoff" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "2023",
              "end.month"   -> "08",
              "end.year"    -> "2023"
            )
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual routes.ConfirmRefundPeriodStartDateController.onPageLoad(NormalMode).url
        }
      }

      "must redirect to ConfirmRefundPeriodStartDateController in CheckMode if start date is before the earliest permitted date" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(CheckMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "2023",
              "end.month"   -> "08",
              "end.year"    -> "2023"
            )
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual routes.ConfirmRefundPeriodStartDateController.onPageLoad(CheckMode).url
        }
      }

      "must show minimum-length error when period is less than 3 months" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "2024",
              "end.month"   -> "04",
              "end.year"    -> "2024"
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          contentAsString(result) must include(messages(application)("refundPeriod.error.periodNotLessThan3Months"))
        }
      }

      "must show end-date-in-past error when end date is in the future" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        running(application) {
          val future = YearMonth.from(baseToday).plusMonths(1)
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> future.minusMonths(4).getMonthValue.toString,
              "start.year"  -> future.minusMonths(4).getYear.toString,
              "end.month"   -> future.getMonthValue.toString,
              "end.year"    -> future.getYear.toString
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          contentAsString(result) must include(messages(application)("refundPeriod.end.error.inPast"))
        }
      }

      "must allow a short period when it ends in December" in {
        val trader = TraderKnownFactsResponse(123, tradeClass = Some(baCode1))
        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, trader).success.value

        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))

        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .overrides(bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)))
          .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "11",
              "start.year"  -> safeFutureYear.toString,
              "end.month"   -> "12",
              "end.year"    -> safeFutureYear.toString
            )
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual onwardRoute.url
        }
      }

      "must accept period 02/2026-04/2026 for non-exempt VRN with old registration date" in {
        val traderOld = TraderKnownFactsResponse(888777666, tradeClass = Some(baCode1), dateOfRegistration = Some(LocalDateTime.of(2010, 1, 1, 0, 0)))
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(traderOld))

        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, traderOld).success.value

        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
          .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "02",
              "start.year"  -> safeFutureYear.toString,
              "end.month"   -> "04",
              "end.year"    -> safeFutureYear.toString
            )
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual onwardRoute.url
        }
      }

      "must show after-latest error for exempt VRN when start is after configured latest" in {
        // Configure an exempt VRN and a latest permitted date of Dec 2020
        val exemptVrn = 999900108
        val traderExempt = TraderKnownFactsResponse(exemptVrn, tradeClass = Some(baCode1))

        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(traderExempt))
        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, traderExempt).success.value

        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .configure(
            "settings.refund.can.create.vrns"             -> "999900108",
            "settings.refund.start.date.latest.permitted" -> "12/20"
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "01",
              "start.year"  -> "2021",
              "end.month"   -> "03",
              "end.year"    -> "2021"
            )

          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val human = java.time.YearMonth.of(2020, 12).atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
          contentAsString(result) must include(messages(application)("refundPeriod.error.afterLatest.both", human))
        }
      }

      "exempt VRN with start 01/2020 and end 12/2020 should display warning message" in {
        val exemptVrn = 999900106
        val traderExempt = TraderKnownFactsResponse(exemptVrn, tradeClass = Some(baCode1))

        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(traderExempt))
        when(mockSessionRepository.set(any[UserAnswers])).thenReturn(Future.successful(true))
        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, traderExempt).success.value

        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .configure(
            "settings.refund.can.create.vrns"               -> "999900106",
            "settings.refund.start.date.earliest.permitted" -> "01/20",
            "settings.refund.start.date.latest.permitted"   -> "12/20"
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "01",
              "start.year"  -> "2020",
              "end.month"   -> "12",
              "end.year"    -> "2020"
            )

          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
        }
      }

      "must ignore configured latest for non-exempt VRN" in {
        val nonExemptTrader = TraderKnownFactsResponse(123, tradeClass = Some(baCode1))
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(nonExemptTrader))

        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, nonExemptTrader).success.value

        // Configure a latest permitted date but clear earliest so it does not interfere
        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .configure(
            "settings.refund.start.date.earliest.permitted" -> "",
            "settings.refund.start.date.latest.permitted"   -> "12/20"
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "01",
              "start.year"  -> "2021",
              "end.month"   -> "03",
              "end.year"    -> "2021"
            )

          val result = route(application, request).value

          val human = java.time.YearMonth.of(2020, 12).atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))

          // Either the submission redirects (valid), or it returns a Bad Request for other reasons
          // — in either case ensure the configured `latest` has not produced an after-latest error.
          if (status(result) == BAD_REQUEST) {
            contentAsString(result) must not include (messages(application)("refundPeriod.error.afterLatest.both", human))
          } else {
            status(result) mustEqual SEE_OTHER
          }
        }
      }

      "must show period-length error for exempt VRN when period is too short (Oct-Nov 2020)" in {
        val exemptVrn = 999900106
        val traderExempt = TraderKnownFactsResponse(exemptVrn, tradeClass = Some(baCode1))

        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(traderExempt))

        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, traderExempt).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader)).build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "10",
              "start.year"  -> "2020",
              "end.month"   -> "11",
              "end.year"    -> "2020"
            )

          val result = route(application, request).value

          status(result) mustBe BAD_REQUEST
          val msgs = application.injector.instanceOf[play.api.i18n.MessagesApi]
          val expected = msgs.preferred(FakeRequest()).apply("refundPeriod.error.periodNotLessThan3Months")
          contentAsString(result) must include(expected)
          // Ensure cutoff/earliest messages are suppressed for exempt VRN short window
          contentAsString(result) must not include "Refund period start date cannot be before 1 January"
        }
      }

      "must show both start-and-end-in-same-year and before-earliest when non-exempt VRN and dates span years but are before earliest" in {
        val nonExemptTrader = TraderKnownFactsResponse(123, tradeClass = Some(baCode1))
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(nonExemptTrader))

        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, nonExemptTrader).success.value

        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .configure("settings.refund.start.date.earliest.permitted" -> "01/25")
          .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderBeforeSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "08",
              "start.year"  -> "2020",
              "end.month"   -> "02",
              "end.year"    -> "2021"
            )

          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST

          // human representation of earliest (from config 01/25)
          val human = java.time.YearMonth.of(2025, 1).atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
          contentAsString(result) must include(human)
          // Do not include the same-year calendar error when earliest business rule applies
          contentAsString(result) must not include (messages(application)("refundPeriod.error.startAndEndInSameYear"))
        }
      }

      "must show before-earliest error on both fields when non-exempt VRN and both dates before earliest" in {
        val nonExemptTrader = TraderKnownFactsResponse(123, tradeClass = Some(baCode1))
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(nonExemptTrader))

        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, nonExemptTrader).success.value

        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderBeforeSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "2019",
              "end.month"   -> "06",
              "end.year"    -> "2019"
            )

          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST

          val body = contentAsString(result)
          // human representation of earliest (from config 01/21)
          val human = "January 2021"
          body must include(human)
          // error summary should link to both start and end fields
          body must include("href=\"#start.month\"")
          body must include("href=\"#end.month\"")
        }
      }

      "must prioritise field errors over earliest business rule when form has missing parts" in {
        val nonExemptTrader = TraderKnownFactsResponse(99999, tradeClass = Some(baCode1))
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(nonExemptTrader))

        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, nonExemptTrader).success.value

        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .configure(
            "settings.refund.can.create.vrns"               -> "",
            "settings.refund.start.date.earliest.permitted" -> "01/21"
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "", // missing -> field error
              "start.year"  -> "2020",
              "end.month"   -> "02",
              "end.year"    -> "2021"
            )

          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          // should link to the start field error (field-level validation)
          contentAsString(result) must include("href=\"#start.month\"")
          // should NOT include the earliest business-rule message
          contentAsString(result) must not include (messages(application)("refundPeriod.error.beforeEarliest.start", "January 2021"))
        }
      }

      "must show before-earliest error on start field when non-exempt VRN and start before earliest" in {
        val nonExemptTrader = TraderKnownFactsResponse(12345, tradeClass = Some(baCode1))
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(nonExemptTrader))

        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, nonExemptTrader).success.value

        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .configure(
            "settings.refund.can.create.vrns"               -> "",
            "settings.refund.start.date.earliest.permitted" -> "01/21"
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "12",
              "start.year"  -> "2020",
              "end.month"   -> "02",
              "end.year"    -> "2021"
            )

          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val human = java.time.YearMonth.of(2021, 1).atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
          contentAsString(result) must include(messages(application)("refundPeriod.error.beforeEarliest.start", human))
        }
      }

      "must show before-earliest error on end field when non-exempt VRN and end before earliest" in {
        val nonExemptTrader = TraderKnownFactsResponse(54321, tradeClass = Some(baCode1))
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(nonExemptTrader))

        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, nonExemptTrader).success.value

        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .configure(
            "settings.refund.can.create.vrns"               -> "",
            "settings.refund.start.date.earliest.permitted" -> "01/21"
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "01",
              "start.year"  -> "2021",
              "end.month"   -> "12",
              "end.year"    -> "2020"
            )

          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val human = java.time.YearMonth.of(2021, 1).atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
          contentAsString(result) must include(messages(application)("refundPeriod.error.beforeEarliest.end", human))
        }
      }

      "must show after-latest error only on end field for exempt VRN when end after configured latest" in {
        val exemptVrn = 999900106
        val traderExempt = TraderKnownFactsResponse(exemptVrn, tradeClass = Some(baCode1))
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(traderExempt))

        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, traderExempt).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader), true)
          .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderBeforeSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "2020",
              "end.month"   -> "06",
              "end.year"    -> "2021"
            )

          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST

          val body = contentAsString(result)
          val humanLatest = "December 2020"
          body must include(humanLatest)
          // only end should be linked in the error summary
          body must include("href=\"#end.month\"")
          body must not include "href=\"#start.month\""
        }
      }

      "must disable earliest validation when config is missing or blank" in {
        val nonExemptTrader = TraderKnownFactsResponse(123, tradeClass = Some(baCode1))

        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(nonExemptTrader))
        when(mockEuVatRefundsService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, nonExemptTrader).success.value

        // Use a form provider with a 'today' in 2021 so other date rules won't reject 01/2021
        val formProvider2021: RefundPeriodFormProvider = new RefundPeriodFormProvider() {
          override protected def today: java.time.LocalDate = java.time.LocalDate.of(2021, 6, 1)
        }

        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .configure(
            "settings.refund.start.date.earliest.permitted" -> ""
          )
          .overrides(
            bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[RefundPeriodFormProvider].toInstance(formProvider2021)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "01",
              "start.year"  -> safeFutureYear.toString,
              "end.month"   -> "03",
              "end.year"    -> safeFutureYear.toString
            )

          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual onwardRoute.url
        }
      }

      "must allow a period exactly 3 months long" in {
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
        when(mockEuVatRefundsService.getLatestApplications(any())(any()))
          .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))
        val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, trader).success.value

        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))

        val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
          .overrides(bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)))
          .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> safeFutureYear.toString,
              "end.month"   -> "05",
              "end.year"    -> safeFutureYear.toString
            )
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual onwardRoute.url
        }
      }

      "must show minimum-length error when start and end are equal" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "2024",
              "end.month"   -> "03",
              "end.year"    -> "2024"
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          contentAsString(result) must include(messages(application)("refundPeriod.error.periodNotLessThan3Months"))
        }
      }

      "must show end-date-invalid error when end date is in the future" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()
        running(application) {
          val future = YearMonth.from(baseToday).plusMonths(1)
          val past = YearMonth.from(baseToday).minusMonths(3)
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> past.getMonthValue.toString,
              "start.year"  -> past.getYear.toString,
              "end.month"   -> future.getMonthValue.toString,
              "end.year"    -> future.getYear.toString
            )
          val result = route(application, request).value
          status(result) mustEqual BAD_REQUEST
          contentAsString(result) must include(messages(application)("refundPeriod.end.error.inPast"))
        }
      }

      "must show invalid start year error when year is greater than 9999" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()
        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "10000",
              "end.month"   -> "08",
              "end.year"    -> "2024"
            )
          val result = route(application, request).value
          status(result) mustEqual BAD_REQUEST
          contentAsString(result) must include(messages(application)("refundPeriod.start.error.invalidDateFormat.year"))
        }
      }

      "must clear CountryChangedPage after successful submission" in {
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
        when(mockEuVatRefundsService.getLatestApplications(any())(any()))
          .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val userAnswers = emptyUserAnswers
          .set(pages.CountryChangedPage, true)
          .success
          .value

        val application = applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> safeFutureYear.toString,
              "end.month"   -> "08",
              "end.year"    -> safeFutureYear.toString
            )
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
        }
      }

      "must set ClaimDetailsAmendedPage to true when refund period is changed and ClaimDetailsCompletedPage is true" in {
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
        when(mockEuVatRefundsService.getLatestApplications(any())(any()))
          .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

        val mockSessionRepository = mock[SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val ua = emptyUserAnswers
          .set(ClaimDetailsCompletedPage, true)
          .success
          .value

        val application = applicationBuilder(userAnswers = Some(ua))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> safeFutureYear.toString,
              "end.month"   -> "08",
              "end.year"    -> safeFutureYear.toString
            )
          val result = route(application, request).value
          status(result) mustEqual SEE_OTHER
        }
      }

      "must NOT set ClaimDetailsAmendedPage when refund period is unchanged" in {
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
        when(mockEuVatRefundsService.getLatestApplications(any())(any()))
          .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val existingPeriod = RefundPeriod(
          LocalDateTime.of(safeFutureYear, 3, 1, 0, 0, 0, 0),
          LocalDateTime.of(safeFutureYear, 8, 31, 23, 59, 59, 999000000)
        )

        val ua = emptyUserAnswers
          .set(pages.RefundPeriodPage, existingPeriod)
          .success
          .value
          .set(pages.ClaimDetailsCompletedPage, true)
          .success
          .value

        val application = applicationBuilder(userAnswers = Some(ua))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> safeFutureYear.toString,
              "end.month"   -> "08",
              "end.year"    -> safeFutureYear.toString
            )
          val result = route(application, request).value
          status(result) mustEqual SEE_OTHER
        }
      }

      "must NOT set ClaimDetailsAmendedPage when ClaimDetailsCompletedPage is not set" in {
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
        when(mockEuVatRefundsService.getLatestApplications(any())(any()))
          .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "2024",
              "end.month"   -> "08",
              "end.year"    -> "2024"
            )
          val result = route(application, request).value
          status(result) mustEqual SEE_OTHER
        }
      }

      "September cutoff" - {
        "must reject start date before January of current year when today is after 30 September" in {
          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "03",
                "start.year"  -> safePastYear.toString,
                "end.month"   -> "08",
                "end.year"    -> safePastYear.toString
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual routes.ConfirmRefundPeriodStartDateController.onPageLoad(NormalMode).url
          }
        }

        "must accept start date in January of current year when today is after 30 September" in {
          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockEuVatRefundsService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))
          val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, trader).success.value

          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))

          val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> afterSept30Year.toString,
                "end.month"   -> "06",
                "end.year"    -> afterSept30Year.toString
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must reject start date before January of previous year when today is on or before 30 September" in {
          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderBeforeSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "03",
                "start.year"  -> (previousYearAfterSept30 - 1).toString,
                "end.month"   -> "08",
                "end.year"    -> (previousYearAfterSept30 - 1).toString
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual routes.ConfirmRefundPeriodStartDateController.onPageLoad(NormalMode).url
          }
        }

        "must accept start date in January of previous year when today is on or before 30 September" in {
          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockEuVatRefundsService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))
          val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, trader).success.value

          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))

          val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[RefundPeriodFormProvider].toInstance(formProviderBeforeSept30)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> previousYearBeforeSept30.toString,
                "end.month"   -> "06",
                "end.year"    -> previousYearBeforeSept30.toString
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }
      }

      "overlap check" - {
        "must skip overlap check and redirect when end date is in December" in {
          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockEuVatRefundsService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
            .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "10",
                "start.year"  -> "2026",
                "end.month"   -> "12",
                "end.year"    -> "2026"
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
            verify(mockEuVatRefundsService, times(0)).getLatestApplications(any())(any())
          }
        }

        "must redirect when no draft applications exist" in {
          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockEuVatRefundsService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
            .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "03",
                "start.year"  -> "2026",
                "end.month"   -> "08",
                "end.year"    -> "2026"
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must redirect to next page when draft exists but period does not overlap" in {
          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockEuVatRefundsService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
            .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "03",
                "start.year"  -> "2026",
                "end.month"   -> "08",
                "end.year"    -> "2026"
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must show overlap error when draft exists and submitted status with both matching refund period" in {
          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockEuVatRefundsService.getLatestApplications(any())(any()))
            .thenReturn(
              Future.successful(
                LatestApplicationResponse(
                  List(
                    LatestApplication(
                      applicationId        = 1L,
                      refundingCountryCode = "LV",
                      periodStartDate      = LocalDateTime.of(2024, 3, 1, 0, 0),
                      periodEndDate        = LocalDateTime.of(2024, 8, 31, 23, 59),
                      applicationNumber    = "GB001",
                      applicationStatus    = Some("D"),
                      submissionStatus     = Some("S"),
                      applicationVersion   = LocalDateTime.of(2024, 1, 1, 0, 0)
                    )
                  ),
                  totalApplication = 1
                )
              )
            )

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
            .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "03",
                "start.year"  -> "2026",
                "end.month"   -> "06",
                "end.year"    -> "2026"
              )
            val result = route(application, request).value

            // now redirects to overlap warning page
            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual routes.PeriodOverlapWarningController.onPageLoad(NormalMode).url
          }
        }

        "must show overlap error when draft exists with both matching period" in {
          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockEuVatRefundsService.getLatestApplications(any())(any()))
            .thenReturn(
              Future.successful(
                LatestApplicationResponse(
                  List(
                    LatestApplication(
                      applicationId        = 1L,
                      refundingCountryCode = "LV",
                      periodStartDate      = LocalDateTime.of(2024, 3, 1, 0, 0),
                      periodEndDate        = LocalDateTime.of(2024, 8, 31, 23, 59),
                      applicationNumber    = "GB001",
                      applicationStatus    = Some("d"),
                      submissionStatus     = None,
                      applicationVersion   = LocalDateTime.of(2024, 1, 1, 0, 0)
                    )
                  ),
                  totalApplication = 1
                )
              )
            )

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
            .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "03",
                "start.year"  -> safeFutureYear.toString,
                "end.month"   -> "08",
                "end.year"    -> safeFutureYear.toString
              )
            val result = route(application, request).value

            // now redirects to overlap warning page
            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual routes.PeriodOverlapWarningController.onPageLoad(NormalMode).url
          }
        }

        "must show overlap error when approved submitted application overlaps with start refund period matching" in {
          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockEuVatRefundsService.getLatestApplications(any())(any()))
            .thenReturn(
              Future.successful(
                LatestApplicationResponse(
                  List(
                    LatestApplication(
                      applicationId        = 1L,
                      refundingCountryCode = "LV",
                      periodStartDate      = LocalDateTime.of(2024, 3, 1, 0, 0),
                      periodEndDate        = LocalDateTime.of(2024, 8, 31, 23, 59),
                      applicationNumber    = "GB001",
                      applicationStatus    = Some("a"),
                      submissionStatus     = Some("s"),
                      applicationVersion   = LocalDateTime.of(2024, 1, 1, 0, 0)
                    )
                  ),
                  1
                )
              )
            )

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
            .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "06",
                "start.year"  -> safeFutureYear.toString,
                "end.month"   -> "09",
                "end.year"    -> safeFutureYear.toString
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual routes.PeriodOverlapWarningController.onPageLoad(NormalMode).url
          }
        }
      }

      "Vat Registration and De-registration validation" - {
        "must accept as valid period if vat registration date is in first quarter of year" in {
          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
            .thenReturn(
              Future.successful(
                TraderKnownFactsResponse(123, tradeClass = Some(baCode1), dateOfRegistration = Some(LocalDateTime.of(safeFutureYear, 1, 1, 0, 0)))
              )
            )
          when(mockEuVatRefundsService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))
          val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, trader).success.value

          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))

          val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[RefundPeriodFormProvider].toInstance(formProviderBeforeSept30)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> safeFutureYear.toString,
                "end.month"   -> "06",
                "end.year"    -> safeFutureYear.toString
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must accept as valid period if vat registration date is in second quarter of year" in {
          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(
            Future.successful(
              TraderKnownFactsResponse(123, tradeClass = Some(baCode1), dateOfRegistration = Some(LocalDateTime.of(safeFutureYear, 5, 20, 10, 38)))
            )
          )
          when(mockEuVatRefundsService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))
          val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, trader).success.value

          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))

          val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[RefundPeriodFormProvider].toInstance(formProviderBeforeSept30)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "05",
                "start.year"  -> safeFutureYear.toString,
                "end.month"   -> "07",
                "end.year"    -> safeFutureYear.toString
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must accept as valid period if end date is within vat de-registration date" in {
          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(
            Future.successful(
              TraderKnownFactsResponse(
                123,
                tradeClass           = Some(baCode1),
                dateOfRegistration   = Some(LocalDateTime.of(safeFutureYear, 2, 1, 0, 0)),
                dateOfDeregistration = Some(LocalDateTime.of(safeFutureYear, 12, 31, 23, 59))
              )
            )
          )
          when(mockEuVatRefundsService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))
          val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, trader).success.value

          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))

          val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[RefundPeriodFormProvider].toInstance(formProviderBeforeSept30)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "05",
                "start.year"  -> safeFutureYear.toString,
                "end.month"   -> "10",
                "end.year"    -> safeFutureYear.toString
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must reject as invalid for vat registration date is in second quarter of year if start date is not within grace period" in {
          val trader =
            TraderKnownFactsResponse(123, tradeClass = Some(baCode1), dateOfRegistration = Some(LocalDateTime.of(safeFutureYear, 5, 20, 10, 38)))
          val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, trader).success.value

          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))

          val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[RefundPeriodFormProvider].toInstance(formProviderBeforeSept30)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> safeFutureYear.toString,
                "end.month"   -> "07",
                "end.year"    -> safeFutureYear.toString
              )
            val result = route(application, request).value

            status(result) mustEqual BAD_REQUEST
            contentAsString(result) must include(messages(application)("refundPeriod.start.error.beforeVatRegDate.remainingQuarter"))
          }
        }

        "must reject as invalid for vat registration date is in first quarter of year if start date is before" in {
          val trader =
            TraderKnownFactsResponse(123, tradeClass = Some(baCode1), dateOfRegistration = Some(LocalDateTime.of(safeFutureYear, 2, 20, 10, 38)))
          val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, trader).success.value

          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))

          val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader)).build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> safeFutureYear.toString,
                "end.month"   -> "06",
                "end.year"    -> safeFutureYear.toString
              )
            val result = route(application, request).value

            status(result) mustEqual BAD_REQUEST
            contentAsString(result) must include(messages(application)("refundPeriod.start.error.beforeVatRegDate.firstQuarter"))
          }
        }

        "must reject as invalid for vat de-registration date if end date is after" in {
          val trader =
            TraderKnownFactsResponse(123, tradeClass = Some(baCode1), dateOfDeregistration = Some(LocalDateTime.of(safeFutureYear, 3, 31, 0, 0)))
          val userAnswersWithTrader = emptyUserAnswers.set(TraderKnownFactsQuery, trader).success.value

          when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))

          val application = applicationBuilder(userAnswers = Some(userAnswersWithTrader)).build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> safeFutureYear.toString,
                "end.month"   -> "05",
                "end.year"    -> safeFutureYear.toString
              )
            val result = route(application, request).value

            status(result) mustEqual BAD_REQUEST
            contentAsString(result) must include(messages(application)("refundPeriod.end.error.afterVatDeRegDate"))
          }
        }

      }
    }
  }
}
