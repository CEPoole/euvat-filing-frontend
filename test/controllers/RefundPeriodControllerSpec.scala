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
import forms.RefundPeriodFormProvider
import models.responses.{LatestApplication, LatestApplicationResponse, TraderKnownFactsResponse}
import models.{NormalMode, RefundPeriod}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.RefundPeriodPage
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import services.EuVatRefundsService

import java.time.LocalDateTime
import scala.concurrent.Future

class RefundPeriodControllerSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  val formProviderBeforeSept30: RefundPeriodFormProvider = new forms.RefundPeriodFormProvider() {
    override protected def today: java.time.LocalDate = java.time.LocalDate.of(2024, 6, 1)
  }

  val formProviderAfterSept30: RefundPeriodFormProvider = new forms.RefundPeriodFormProvider() {
    override protected def today: java.time.LocalDate = java.time.LocalDate.of(2024, 10, 1)
  }

  val onwardRoute: Call = Call("GET", "/foo")
  val mockService: EuVatRefundsService = mock[EuVatRefundsService]
  private val baCode1 = "49200"

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockService)
  }

  "RefundPeriod Controller" - {

    ".onPageLoad" - {
      "must return OK and the correct view for a GET" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, routes.RefundPeriodController.onPageLoad(NormalMode).url)
          val result = route(application, request).value
          val view = application.injector.instanceOf[views.html.RefundPeriodView]
          implicit val msgs: Messages = messages(application)
          val form = application.injector.instanceOf[forms.RefundPeriodFormProvider].apply()

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(
            form,
            NormalMode,
            routes.RefundingLanguageController.onPageLoad(NormalMode),
            None,
            None,
            Set.empty[String],
            Map.empty[String, String]
          )(request, msgs).toString
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
          val formProvider = application.injector.instanceOf[forms.RefundPeriodFormProvider]
          implicit val msgs: Messages = messages(application)
          val start = java.time.YearMonth.of(savedPeriod.startDate.getYear, savedPeriod.startDate.getMonthValue)
          val end = java.time.YearMonth.of(savedPeriod.endDate.getYear, savedPeriod.endDate.getMonthValue)
          val form = formProvider().fill(forms.RefundPeriodData(start, end))

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(
            form,
            NormalMode,
            routes.RefundingLanguageController.onPageLoad(NormalMode),
            None,
            None,
            Set.empty[String],
            Map.empty[String, String]
          )(request, msgs).toString
        }
      }

      "must use RefundingCurrencyController as back link when country has two currencies" in {
        val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "EE").success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, routes.RefundPeriodController.onPageLoad(models.NormalMode).url)
          val result = route(application, request).value

          status(result) mustEqual OK
          contentAsString(result) must include(routes.RefundingCurrencyController.onPageLoad(models.NormalMode).url)
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
        when(mockService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
        when(mockService.getLatestApplications(any())(any()))
          .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[EuVatRefundsService].toInstance(mockService))
          .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
          .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
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
          .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
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
          .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
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
          .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "2023",
              "end.month"   -> "08",
              "end.year"    -> "2024"
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          contentAsString(result) must include("Refund period start date cannot be before 1 January 2024")
        }
      }

      "must show minimum-length error when period is less than 3 months" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
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
          val future = java.time.YearMonth.now().plusMonths(1)
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
        when(mockService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)))
          .overrides(bind[EuVatRefundsService].toInstance(mockService))
          .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "11",
              "start.year"  -> "2024",
              "end.month"   -> "12",
              "end.year"    -> "2024"
            )
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual onwardRoute.url
        }
      }

      "must allow a period exactly 3 months long" in {
        when(mockService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
        when(mockService.getLatestApplications(any())(any()))
          .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)))
          .overrides(bind[EuVatRefundsService].toInstance(mockService))
          .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
            .withFormUrlEncodedBody(
              "start.month" -> "03",
              "start.year"  -> "2024",
              "end.month"   -> "05",
              "end.year"    -> "2024"
            )
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual onwardRoute.url
        }
      }

      "must show minimum-length error when start and end are equal" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
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
          val future = java.time.YearMonth.now().plusMonths(1)
          val past = java.time.YearMonth.now().minusMonths(3)
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
        when(mockService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
        when(mockService.getLatestApplications(any())(any()))
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
            bind[EuVatRefundsService].toInstance(mockService),
            bind[repositories.SessionRepository].toInstance(mockSessionRepository),
            bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30)
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

          import org.mockito.ArgumentCaptor
          val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
          verify(mockSessionRepository, times(1)).set(captor.capture())
          val saved = captor.getValue
          saved.get(pages.CountryChangedPage).isDefined mustBe false
        }
      }

      "must set ClaimDetailsAmendedPage to true when refund period is changed and ClaimDetailsCompletedPage is true" in {
        when(mockService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
        when(mockService.getLatestApplications(any())(any()))
          .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val ua = emptyUserAnswers
          .set(pages.ClaimDetailsCompletedPage, true)
          .success
          .value

        val application = applicationBuilder(userAnswers = Some(ua))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[EuVatRefundsService].toInstance(mockService),
            bind[repositories.SessionRepository].toInstance(mockSessionRepository),
            bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30)
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

          import org.mockito.ArgumentCaptor
          val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
          verify(mockSessionRepository, times(1)).set(captor.capture())
          captor.getValue.get(pages.ClaimDetailsAmendedPage) mustBe Some(true)
        }
      }

      "must NOT set ClaimDetailsAmendedPage when refund period is unchanged" in {
        when(mockService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
        when(mockService.getLatestApplications(any())(any()))
          .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val existingPeriod = RefundPeriod(
          LocalDateTime.of(2024, 3, 1, 0, 0, 0, 0),
          LocalDateTime.of(2024, 8, 31, 23, 59, 59, 999000000)
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
            bind[EuVatRefundsService].toInstance(mockService),
            bind[repositories.SessionRepository].toInstance(mockSessionRepository),
            bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30)
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

          import org.mockito.ArgumentCaptor
          val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
          verify(mockSessionRepository, times(1)).set(captor.capture())
          captor.getValue.get(pages.ClaimDetailsAmendedPage).isDefined mustBe false
        }
      }

      "must NOT set ClaimDetailsAmendedPage when ClaimDetailsCompletedPage is not set" in {
        when(mockService.retrieveTraderKnownFacts()(any()))
          .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
        when(mockService.getLatestApplications(any())(any()))
          .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[EuVatRefundsService].toInstance(mockService),
            bind[repositories.SessionRepository].toInstance(mockSessionRepository),
            bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30)
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

          import org.mockito.ArgumentCaptor
          val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
          verify(mockSessionRepository, times(1)).set(captor.capture())
          captor.getValue.get(pages.ClaimDetailsAmendedPage).isDefined mustBe false
        }
      }

      "September cutoff" - {
        "must reject start date before January of current year when today is after 30 September" in {
          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
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

            status(result) mustEqual BAD_REQUEST
            contentAsString(result) must include("Refund period start date cannot be before 1 January 2024")
          }
        }

        "must accept start date in January of current year when today is after 30 September" in {
          when(mockService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30),
              bind[EuVatRefundsService].toInstance(mockService)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> "2024",
                "end.month"   -> "06",
                "end.year"    -> "2024"
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must reject start date before January of previous year when today is on or before 30 September" in {
          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderBeforeSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "03",
                "start.year"  -> "2022",
                "end.month"   -> "08",
                "end.year"    -> "2022"
              )
            val result = route(application, request).value

            status(result) mustEqual BAD_REQUEST
            contentAsString(result) must include("Refund period start date cannot be before 1 January 2023")
          }
        }

        "must accept start date in January of previous year when today is on or before 30 September" in {
          when(mockService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[EuVatRefundsService].toInstance(mockService),
              bind[forms.RefundPeriodFormProvider].toInstance(formProviderBeforeSept30)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> "2023",
                "end.month"   -> "06",
                "end.year"    -> "2023"
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }
      }

      "overlap check" - {
        "must skip overlap check and redirect when end date is in December" in {
          when(mockService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
            .overrides(bind[EuVatRefundsService].toInstance(mockService))
            .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "10",
                "start.year"  -> "2024",
                "end.month"   -> "12",
                "end.year"    -> "2024"
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
            verify(mockService, times(0)).getLatestApplications(any())(any())
          }
        }

        "must redirect when no draft applications exist" in {
          when(mockService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
            .overrides(bind[EuVatRefundsService].toInstance(mockService))
            .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
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
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must redirect to next page when draft exists but period does not overlap" in {
          when(mockService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[Navigator].toInstance(new FakeNavigator(onwardRoute)))
            .overrides(bind[EuVatRefundsService].toInstance(mockService))
            .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "03",
                "start.year" -> "2024",
                "end.month" -> "08",
                "end.year" -> "2024"
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must show overlap error when draft exists and submitted status with both matching refund period" in {
          when(mockService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockService.getLatestApplications(any())(any()))
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
            .overrides(bind[EuVatRefundsService].toInstance(mockService))
            .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "03",
                "start.year"  -> "2024",
                "end.month"   -> "06",
                "end.year"    -> "2024"
              )
            val result = route(application, request).value

            // TODO: update to warning page redirect once designed
            status(result) mustEqual BAD_REQUEST
            contentAsString(result) must include(messages(application)("refundPeriod.error.overlap"))
          }
        }

        "must show overlap error when draft exists with both matching period" in {
          when(mockService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockService.getLatestApplications(any())(any()))
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
            .overrides(bind[EuVatRefundsService].toInstance(mockService))
            .overrides(bind[forms.RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
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

            // TODO: update to warning page redirect once designed
            status(result) mustEqual BAD_REQUEST
            contentAsString(result) must include(messages(application)("refundPeriod.error.overlap"))
          }
        }

        "must show overlap error when approved submitted application overlaps with start refund period matching" in {
          when(mockService.retrieveTraderKnownFacts()(any()))
            .thenReturn(Future.successful(TraderKnownFactsResponse(123, tradeClass = Some(baCode1))))
          when(mockService.getLatestApplications(any())(any()))
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
            .overrides(bind[EuVatRefundsService].toInstance(mockService))
            .overrides(bind[RefundPeriodFormProvider].toInstance(formProviderAfterSept30))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "06",
                "start.year"  -> "2024",
                "end.month"   -> "09",
                "end.year"    -> "2024"
              )
            val result = route(application, request).value

            status(result) mustEqual BAD_REQUEST
            contentAsString(result) must include(messages(application)("refundPeriod.error.overlap"))
          }
        }
      }

      "Business Function F6" - {
        "must accept as valid period if vat registration date is in first quarter of year" in {
          when(mockService.retrieveTraderKnownFacts()(any()))
            .thenReturn(
              Future.successful(
                TraderKnownFactsResponse(123, tradeClass = Some(baCode1), dateOfRegistration = Some(LocalDateTime.of(2025, 1, 1, 0, 0)))
              )
            )
          when(mockService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[EuVatRefundsService].toInstance(mockService),
              bind[forms.RefundPeriodFormProvider].toInstance(formProviderBeforeSept30)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> "2025",
                "end.month"   -> "06",
                "end.year"    -> "2025"
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must accept as valid period if vat registration date is in second quarter of year" in {
          when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(
            Future.successful(
              TraderKnownFactsResponse(123, tradeClass = Some(baCode1), dateOfRegistration = Some(LocalDateTime.of(2025, 5, 20, 10, 38)))
            )
          )
          when(mockService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[EuVatRefundsService].toInstance(mockService),
              bind[forms.RefundPeriodFormProvider].toInstance(formProviderBeforeSept30)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "05",
                "start.year"  -> "2025",
                "end.month"   -> "07",
                "end.year"    -> "2025"
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must accept as valid period if end date is within vat de-registration date" in {
          when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(
            Future.successful(
              TraderKnownFactsResponse(123,
                                       tradeClass           = Some(baCode1),
                                       dateOfRegistration   = Some(LocalDateTime.of(2025, 2, 1, 0, 0)),
                                       dateOfDeregistration = Some(LocalDateTime.of(2025, 12, 31, 23, 59))
                                      )
            )
          )
          when(mockService.getLatestApplications(any())(any()))
            .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[EuVatRefundsService].toInstance(mockService),
              bind[forms.RefundPeriodFormProvider].toInstance(formProviderBeforeSept30)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "05",
                "start.year"  -> "2025",
                "end.month"   -> "10",
                "end.year"    -> "2025"
              )
            val result = route(application, request).value

            status(result) mustEqual SEE_OTHER
            redirectLocation(result).value mustEqual onwardRoute.url
          }
        }

        "must reject as invalid for vat registration date is in second quarter of year if start date is not within grace period" in {
          when(mockService.retrieveTraderKnownFacts()(any())).thenReturn(
            Future.successful(
              TraderKnownFactsResponse(123, tradeClass = Some(baCode1), dateOfRegistration = Some(LocalDateTime.of(2025, 5, 20, 10, 38)))
            )
          )

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(
              bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
              bind[EuVatRefundsService].toInstance(mockService),
              bind[forms.RefundPeriodFormProvider].toInstance(formProviderBeforeSept30)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> "2025",
                "end.month"   -> "07",
                "end.year"    -> "2025"
              )
            val result = route(application, request).value

            status(result) mustEqual BAD_REQUEST
            contentAsString(result) must include(messages(application)("refundPeriod.start.error.beforeVatRegDate.remainingQuarter"))
          }
        }

        "must reject as invalid for vat registration date is in first quarter of year if start date is before" in {
          when(mockService.retrieveTraderKnownFacts()(any()))
            .thenReturn(
              Future.successful(
                TraderKnownFactsResponse(123, tradeClass = Some(baCode1), dateOfRegistration = Some(LocalDateTime.of(2025, 2, 20, 10, 38)))
              )
            )

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(
              bind[EuVatRefundsService].toInstance(mockService)
            )
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> "2025",
                "end.month"   -> "06",
                "end.year"    -> "2025"
              )
            val result = route(application, request).value

            status(result) mustEqual BAD_REQUEST
            contentAsString(result) must include(messages(application)("refundPeriod.start.error.beforeVatRegDate.firstQuarter"))
          }
        }

        "must reject as invalid for vat de-registration date if end date is after" in {
          when(mockService.retrieveTraderKnownFacts()(any()))
            .thenReturn(
              Future.successful(
                TraderKnownFactsResponse(123, tradeClass = Some(baCode1), dateOfDeregistration = Some(LocalDateTime.of(2026, 3, 31, 0, 0)))
              )
            )

          val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
            .overrides(bind[EuVatRefundsService].toInstance(mockService))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.RefundPeriodController.onSubmit(NormalMode).url)
              .withFormUrlEncodedBody(
                "start.month" -> "01",
                "start.year"  -> "2026",
                "end.month"   -> "05",
                "end.year"    -> "2026"
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
