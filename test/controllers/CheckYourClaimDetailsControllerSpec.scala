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
import models.responses.{ApplicationResponse, LatestApplication, LatestApplicationResponse}
import models.{ContactDetails, RefundPeriod, RefundingLanguage}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar
import pages.*
import play.api.inject.bind
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import queries.LatestCountryResponseQuery
import repositories.SessionRepository
import viewmodels.govuk.SummaryListFluency

import java.time.LocalDateTime
import scala.concurrent.Future

class CheckYourClaimDetailsControllerSpec extends SpecBase with SummaryListFluency with MockitoSugar {

  "Check Your Answers Controller" - {
    // use shared mock from SpecBase
    "must return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()
      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url).withCSRFToken
        val result = route(application, request).value

        status(result) mustEqual OK
      }
    }

    "must redirect to the task list on submit and set ClaimDetailsCompletedPage to true" in {
      val ua = emptyUserAnswers
        .set(RefundingCountryPage, "BE")
        .success
        .value
        .set(ClaimDetailsCompletedPage, true)
        .success
        .value
        .set(RefundingCurrencyPage, "eur")
        .success
        .value
        .set(RefundingLanguagePage, RefundingLanguage.English)
        .success
        .value
        .set(RefundPeriodPage, RefundPeriod.apply(LocalDateTime.of(2025, 4, 1, 10, 10, 10, 10), LocalDateTime.of(2025, 12, 31, 23, 2, 10, 10)))
        .success
        .value
        .set(ContactDetailsPage, ContactDetails("test@email.com", Some("07123456789")))
        .success
        .value
        .set(BusinessActivityCodePage, "9999")
        .success
        .value

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
      when(mockEuVatRefundsService.createApplication(any())(any()))
        .thenReturn(Future.successful(ApplicationResponse(123, "GB123456789", 10)))

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.CheckYourClaimDetailsController.onSubmit().url)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.TaskListDashboardController.onPageLoad().url
      }
    }

    "must still redirect to task list on submit even if sessionRepository.set returns false" in {
      val ua = emptyUserAnswers
        .set(RefundingCountryPage, "BE")
        .success
        .value
        .set(ClaimDetailsCompletedPage, true)
        .success
        .value
        .set(RefundingCurrencyPage, "eur")
        .success
        .value
        .set(RefundingLanguagePage, RefundingLanguage.English)
        .success
        .value
        .set(RefundPeriodPage, RefundPeriod.apply(LocalDateTime.of(2025, 4, 1, 10, 10, 10, 10), LocalDateTime.of(2025, 12, 31, 23, 2, 10, 10)))
        .success
        .value
        .set(ContactDetailsPage, ContactDetails("test@email.com", None))
        .success
        .value
        .set(BusinessActivityCodePage, "9999")
        .success
        .value

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(false)
      when(mockEuVatRefundsService.createApplication(any())(any()))
        .thenReturn(Future.successful(ApplicationResponse(123, "GB123456789", 10)))

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.CheckYourClaimDetailsController.onSubmit().url)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.TaskListDashboardController.onPageLoad().url
      }
    }

    "must redirect to JourneyRecovery on submit when duplicate application exists" in {
      val dupApp = LatestApplication(1L, "BE", LocalDateTime.now(), LocalDateTime.now(), "appNo", Some("D"), None, LocalDateTime.now())
      val ua = emptyUserAnswers
        .set(RefundingCountryPage, "BE")
        .success
        .value
        .set(RefundingCurrencyPage, "eur")
        .success
        .value
        .set(RefundingLanguagePage, RefundingLanguage.English)
        .success
        .value
        .set(RefundPeriodPage, RefundPeriod.apply(LocalDateTime.of(2025, 4, 1, 10, 10, 10, 10), LocalDateTime.of(2025, 12, 31, 23, 2, 10, 10)))
        .success
        .value
        .set(ContactDetailsPage, ContactDetails("test@email.com", Some("07123456789")))
        .success
        .value
        .set(BusinessActivityCodePage, "9999")
        .success
        .value

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
      when(mockEuVatRefundsService.getLatestApplications(any())(any())).thenReturn(Future.successful(List(dupApp), 1))

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.CheckYourClaimDetailsController.onSubmit().url)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url

        // ensure we did not attempt to create the application
        verify(mockEuVatRefundsService, times(0)).createApplication(any())(any())
      }
    }

    "must NOT include language label when country has only one language" in {
      val ua = emptyUserAnswers
        .set(RefundingCountryPage, "CZ")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua)).build()

      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url).withCSRFToken
        val result = route(application, request).value
        val html = contentAsString(result)

        html must not include messages(application)("checkYourClaimDetails.refundingLanguage.label")
      }
    }

    "must include change links for each section" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url).withCSRFToken
        val result = route(application, request).value
        val html = contentAsString(result)

        html must include("change")
      }
    }

    "must not display phone number when it's missing" in {
      val contact = models.ContactDetails("a@b.com", None)
      val ua = emptyUserAnswers.set(ContactDetailsPage, contact).success.value
      val application = applicationBuilder(userAnswers = Some(ua)).build()

      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url).withCSRFToken
        val result = route(application, request).value
        val html = contentAsString(result)

        html must include(contact.email)
        html must include(messages(application)("checkYourClaimDetails.contactPhone.subLabel"))
        html must include(messages(application)("Not provided"))
      }
    }

    "must include language row with change link when country has multiple languages" in {
      val ua = emptyUserAnswers
        .set(RefundingCountryPage, "BE")
        .success
        .value
        .set(RefundingLanguagePage, models.RefundingLanguage.English)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua)).build()

      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url).withCSRFToken
        val result = route(application, request).value
        val html = contentAsString(result)

        html must include(messages(application)("checkYourClaimDetails.refundingLanguage.label"))
        html must include(routes.RefundingLanguageController.onPageLoad(models.CheckMode).url)
      }
    }

    "must NOT include language section when country has only one language" in {
      val ua = emptyUserAnswers
        .set(RefundingCountryPage, "CZ")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua)).build()

      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url).withCSRFToken
        val result = route(application, request).value
        val html = contentAsString(result)

        html must not include messages(application)("checkYourClaimDetails.refundingLanguage.label")
      }
    }

    "must NOT include currency section when country has only one currency" in {
      val ua = emptyUserAnswers
        .set(RefundingCountryPage, "AT")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua)).build()

      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url).withCSRFToken
        val result = route(application, request).value
        val html = contentAsString(result)

        html must not include messages(application)("checkYourClaimDetails.refundingCurrency.label")
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must render post submission view when ClaimDetailsCompletedPage is true" in {
      val ua = emptyUserAnswers.set(ClaimDetailsCompletedPage, true).success.value

      val application = applicationBuilder(userAnswers = Some(ua)).build()

      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url).withCSRFToken
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include(messages(application)("checkYourClaimDetails.claimDetails.heading"))
      }
    }

    "must render pre submission view when ClaimDetailsCompletedPage is not set" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url).withCSRFToken
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include(messages(application)("checkYourClaimDetails.heading"))
      }
    }

    "must show Save and continue button when ClaimDetailsAmendedPage is true" in {
      val ua = emptyUserAnswers
        .set(ClaimDetailsCompletedPage, true)
        .success
        .value
        .set(ClaimDetailsAmendedPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua)).build()

      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url).withCSRFToken
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include(messages(application)("site.save.continue"))
      }
    }

    "must show Continue button when post submission and ClaimDetailsAmendedPage is not set" in {
      val ua = emptyUserAnswers.set(ClaimDetailsCompletedPage, true).success.value

      val application = applicationBuilder(userAnswers = Some(ua)).build()

      running(application) {
        val request = FakeRequest(GET, routes.CheckYourClaimDetailsController.onPageLoad().url).withCSRFToken
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) must include(messages(application)("site.continue"))
      }
    }

    "must NOT call createApplication or sessionRepository when post-submission and not amended" in {
      val mockSessionRepository = mock[SessionRepository]
      val ua = emptyUserAnswers.set(ClaimDetailsCompletedPage, true).success.value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val result = route(application, FakeRequest(POST, routes.CheckYourClaimDetailsController.onSubmit().url)).value
        status(result) mustEqual SEE_OTHER
        verify(mockEuVatRefundsService, never()).createApplication(any())(any())
        verify(mockSessionRepository, never()).set(any())
      }
    }

    "must call createApplication when post-submission and amended" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
      when(mockEuVatRefundsService.createApplication(any())(any()))
        .thenReturn(Future.successful(ApplicationResponse(123, "GB123456789", 10)))

      val ua = emptyUserAnswers
        .set(ClaimDetailsCompletedPage, true)
        .success
        .value
        .set(ClaimDetailsAmendedPage, true)
        .success
        .value
        .set(RefundingCountryPage, "DE")
        .success
        .value
        .set(RefundingCurrencyPage, "eur")
        .success
        .value
        .set(RefundingLanguagePage, RefundingLanguage.English)
        .success
        .value
        .set(RefundPeriodPage, RefundPeriod.apply(LocalDateTime.of(2025, 4, 1, 10, 10, 10, 10), LocalDateTime.of(2025, 12, 31, 23, 2, 10, 10)))
        .success
        .value
        .set(ContactDetailsPage, ContactDetails("test@email.com", Some("07123456789")))
        .success
        .value
        .set(BusinessActivityCodePage, "9999")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.CheckYourClaimDetailsController.onSubmit().url)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        verify(mockEuVatRefundsService, times(1)).createApplication(any())(any())
      }
    }

    "must clear ClaimDetailsAmendedPage on submit when post submission" in {
      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
      when(mockEuVatRefundsService.createApplication(any())(any()))
        .thenReturn(Future.successful(ApplicationResponse(123, "GB123456789", 10)))

      val ua = emptyUserAnswers
        .set(ClaimDetailsCompletedPage, true)
        .success
        .value
        .set(ClaimDetailsAmendedPage, true)
        .success
        .value
        .set(RefundingCountryPage, "DE")
        .success
        .value
        .set(RefundingCurrencyPage, "eur")
        .success
        .value
        .set(RefundingLanguagePage, RefundingLanguage.English)
        .success
        .value
        .set(RefundPeriodPage, RefundPeriod.apply(LocalDateTime.of(2025, 4, 1, 10, 10, 10, 10), LocalDateTime.of(2025, 12, 31, 23, 2, 10, 10)))
        .success
        .value
        .set(ContactDetailsPage, ContactDetails("test@email.com", Some("07123456789")))
        .success
        .value
        .set(BusinessActivityCodePage, "9999")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.CheckYourClaimDetailsController.onSubmit().url)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(ClaimDetailsAmendedPage).isDefined mustBe false
      }
    }
  }
}
