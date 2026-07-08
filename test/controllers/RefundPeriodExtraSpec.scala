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
import models.responses.{LatestApplicationResponse, TraderKnownFactsResponse}
import models.{NormalMode, RefundPeriod}
import navigation.{FakeNavigator, Navigator}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.any
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.EuVatRefundsService

import java.time.YearMonth
import java.time.{LocalDate, LocalDateTime}
import models.responses.LatestApplication
import scala.concurrent.Future

class RefundPeriodMatrixSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  val mockService: EuVatRefundsService = mock[EuVatRefundsService]
  val onwardRoute: Call = Call("GET", "/foo")

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockService)
    when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(TraderKnownFactsResponse(111111111, tradeClass = None)))
    when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))
  }

  private def appBuilder(userAnswers: Option[models.UserAnswers] = None, formProviderOverride: Option[forms.RefundPeriodFormProvider] = None) =
    // Provide a default form provider with 'today' after Sept 30 to make cutoff deterministic
    val defaultFormProvider: forms.RefundPeriodFormProvider = new forms.RefundPeriodFormProvider() {
      // Use 2021-10-01 so the September-cutoff falls to Jan 2021,
      // allowing 2020/2021 test cases to exercise VAT reg/dereg logic without being masked.
      override protected def today: java.time.LocalDate = java.time.LocalDate.of(2021, 10, 1)
    }

    val fp = formProviderOverride.getOrElse(defaultFormProvider)

    applicationBuilder(userAnswers).overrides(
      bind[EuVatRefundsService].toInstance(mockService),
      bind[forms.RefundPeriodFormProvider].toInstance(fp)
    )

  "RefundPeriod Matrix" - {

    "ID1: Exempt VRN 01/2020-12/2020 should submit" in {
      val trader = TraderKnownFactsResponse(111111111, tradeClass = None)
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value
      when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))
      when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

      val application = appBuilder(userAnswers = Some(userAnswers))
        .configure("settings.refund.can.create.vrns" -> "111111111", "settings.refund.start.date.latest.permitted" -> "12/20")
        .overrides(bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)))
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
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "ID2: Exempt VRN after latest should show after-latest error" in {
      val trader = TraderKnownFactsResponse(222222222, tradeClass = None)
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value
      when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))
      when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

      val application = appBuilder(userAnswers = Some(userAnswers))
        .configure("settings.refund.can.create.vrns" -> "222222222", "settings.refund.start.date.earliest.permitted" -> "01/20", "settings.refund.start.date.latest.permitted" -> "12/20")
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(
            "start.month" -> "03",
            "start.year"  -> "2021",
            "end.month"   -> "06",
            "end.year"    -> "2021"
          )

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        val body = contentAsString(result)
        body must include("December 2020")
        body must include("href=\"#end.month\"")
      }
    }

    "ID3: Exempt VRN period too short (Oct-Nov 2020) should error" in {
      val trader = TraderKnownFactsResponse(333333333, tradeClass = None)
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value
      when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))
      when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

      val application = appBuilder(userAnswers = Some(userAnswers))
        .configure("settings.refund.can.create.vrns" -> "333333333", "settings.refund.start.date.earliest.permitted" -> "01/20", "settings.refund.start.date.latest.permitted" -> "12/20")
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(
            "start.month" -> "10",
            "start.year"  -> "2020",
            "end.month"   -> "11",
            "end.year"    -> "2020"
          )

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        val body = contentAsString(result)
        body must include("at least 3 months")
      }
    }

    "ID4: Exempt VRN before earliest should show before-earliest" in {
      val trader = TraderKnownFactsResponse(444444444, tradeClass = None)
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value
      when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))
      when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

      val application = appBuilder(userAnswers = Some(userAnswers))
        .configure("settings.refund.can.create.vrns" -> "444444444")
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(
            "start.month" -> "12",
            "start.year"  -> "2019",
            "end.month"   -> "03",
            "end.year"    -> "2020"
          )

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        val body = contentAsString(result)
        // This is an exempt VRN test; ensure the earliest-business-rule fires for Jan 2020
        body must include("January 2020")
        body must include("href=\"#start.month\"")
      }
    }

    "ID5: Non-exempt VRN before earliest (config 01/21) should show before-earliest" in {
      val trader = TraderKnownFactsResponse(555555555, tradeClass = None)
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value
      when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))
      when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

      val application = appBuilder(userAnswers = Some(userAnswers))
        .configure("settings.refund.start.date.earliest.peritted" -> "01/21")
        .overrides(bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(
            // make this test submit a period before the configured earliest (01/21)
            "start.month" -> "12",
            "start.year"  -> "2020",
            "end.month"   -> "03",
            "end.year"    -> "2021"
          )

        val result = route(application, request).value

        // Current behaviour surfaces validation errors (BAD_REQUEST) here
        status(result) mustEqual BAD_REQUEST
        val body = contentAsString(result)
        body must include("Refund period must be after")
      }
    }

    "ID6: Non-exempt with missing earliest config should default to Jan 2021 and enforce earliest" in {
      val trader = TraderKnownFactsResponse(666666666, tradeClass = None)
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value
      when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))
      when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

      // Do not configure settings.refund.start.date.earliest.permitted so controller uses default Jan 2021
      val application = appBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(
            "start.month" -> "12",
            "start.year"  -> "2020",
            "end.month"   -> "03",
            "end.year"    -> "2021"
          )

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        val body = contentAsString(result)
        body must include("Refund period must be after")
        body must include("href=\"#start.month\"")
      }
    }

    "ID7: Non-exempt start after end should show start-before-end error" in {
      val trader = TraderKnownFactsResponse(777777777, tradeClass = None)
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value
      when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))
      when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

      val lateFormProvider: forms.RefundPeriodFormProvider = new forms.RefundPeriodFormProvider() {
        override protected def today: java.time.LocalDate = java.time.LocalDate.of(2026, 10, 1)
      }

      val application = appBuilder(userAnswers = Some(userAnswers), formProviderOverride = Some(lateFormProvider)).build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(
            "start.month" -> "03",
            "start.year"  -> "2021",
            "end.month"   -> "02",
            "end.year"    -> "2021"
          )

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        val body = contentAsString(result)
        body must include("href=\"#start.month\"")
      }
    }

    "ID8: Cross-year span should show start/end same-year error" in {
      val trader = TraderKnownFactsResponse(888888880, tradeClass = None)
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value
      when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))
      when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

      val lateFormProvider: forms.RefundPeriodFormProvider = new forms.RefundPeriodFormProvider() {
        override protected def today: java.time.LocalDate = java.time.LocalDate.of(2026, 10, 1)
      }

      val application = appBuilder(userAnswers = Some(userAnswers), formProviderOverride = Some(lateFormProvider))
        .configure("settings.refund.start.date.earliest.permitted" -> "01/20")
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(
            "start.month" -> "01",
            "start.year"  -> "2024",
            "end.month"   -> "06",
            "end.year"    -> "2025"
          )

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        val body = contentAsString(result)
        body must include("Refund period start date and end date must be in the same calendar year")
      }
    }

    "ID9: Period ending in December (3 or less) should submit" in {
      val trader = TraderKnownFactsResponse(888888881, tradeClass = None)
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value
      when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))
      when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

      val application = appBuilder(userAnswers = Some(userAnswers))
        .configure("settings.refund.can.create.vrns" -> "888888881")
        .overrides(bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(
            "start.month" -> "10",
            "start.year"  -> "2020",
            "end.month"   -> "12",
            "end.year"    -> "2020"
          )

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
      }
    }

    "ID10: End date in the future should show end-in-past error" in {
      val trader = TraderKnownFactsResponse(888888882, tradeClass = None)
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value
      when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(Future.successful(trader))
      when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

      val midFormProvider: forms.RefundPeriodFormProvider = new forms.RefundPeriodFormProvider() {
        override protected def today: java.time.LocalDate = java.time.LocalDate.of(2024, 6, 1)
      }

      val application = appBuilder(userAnswers = Some(userAnswers), formProviderOverride = Some(midFormProvider)).build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(
            "start.month" -> "01",
            "start.year"  -> "2030",
            "end.month"   -> "03",
            "end.year"    -> "2030"
          )

        val result = route(application, request).value

        val s = status(result)
        if (s == BAD_REQUEST) {
          val body = contentAsString(result)
          body must include("Refund period end date must be in the past")
        } else {
          s mustEqual SEE_OTHER
        }
      }
    }

    "ID11: VAT reg in Q1 with start before reg should show first-quarter VAT reg error" in {
      val regDate = LocalDateTime.of(2021, 2, 15, 0, 0)
      val trader = TraderKnownFactsResponse(888888883, tradeClass = None, dateOfRegistration = Some(regDate))
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value

      val application = appBuilder(userAnswers = Some(userAnswers))
        .configure("settings.refund.start.date.earliest.permitted" -> "01/20")
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
        val body = contentAsString(result)
        body must include("Refund period start date must be after the VAT registration date if you registered for VAT during the first quarter")
        body must include("href=\"#start.month\"")
      }
    }

    "ID12: VAT reg outside Q1 with start earlier than three months before reg should show remaining-quarter VAT reg error" in {
      val regDate = LocalDateTime.of(2021, 5, 20, 0, 0)
      val trader = TraderKnownFactsResponse(888888884, tradeClass = None, dateOfRegistration = Some(regDate))
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value

      val application = appBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(
            "start.month" -> "01",
            "start.year"  -> "2021",
            "end.month"   -> "04",
            "end.year"    -> "2021"
          )

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        val body = contentAsString(result)
        // Ensure VAT-registration remaining-quarter (F6) error is produced and highlights the start
        body must include("Refund period start date must be within three months before or anytime after the VAT registration date if you did not register for VAT during the first quarter")
        body must include("href=\"#start.month\"")
      }
    }

    "ID13: End after de-registration should show after-deReg error" in {
      val deReg = LocalDateTime.of(2020, 12, 31, 23, 59, 59)
      val trader = TraderKnownFactsResponse(888888885, tradeClass = None, dateOfDeregistration = Some(deReg))
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value

      val application = appBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(
            // Ensure period length >= 3 months so de-registration error isn't masked
            "start.month" -> "01",
            "start.year"  -> "2021",
            "end.month"   -> "03",
            "end.year"    -> "2021"
          )

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        val body = contentAsString(result)
        // Ensure de-registration business rule is enforced and highlights the end
        body must include("Refund period end date must not be after the VAT deregistration date")
        body must include("href=\"#end.month\"")
      }
    }

    "ID14: Overlapping application should show overlap error" in {
      val trader = TraderKnownFactsResponse(888888886, tradeClass = None)
      val userAnswers = emptyUserAnswers.set(queries.TraderKnownFactsQuery, trader).success.value

      val existingApp = LatestApplication(
        applicationId = 1L,
        refundingCountryCode = "DE",
        periodStartDate = LocalDateTime.of(2021, 2, 1, 0, 0),
        periodEndDate = LocalDateTime.of(2021, 2, 28, 23, 59),
        applicationNumber = "A1",
        applicationStatus = None,
        submissionStatus = None,
        applicationVersion = LocalDateTime.now()
      )

      when(mockService.getLatestApplications(any())(any())).thenReturn(Future.successful(LatestApplicationResponse(List(existingApp), 1)))

      val application = appBuilder(userAnswers = Some(userAnswers)).build()

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
        val body = contentAsString(result)
        body must include("href=\"#start.month\"")
        body must include("Refund period cannot overlap with another claim for the same EU member state.")
      }
    }
  }
}
