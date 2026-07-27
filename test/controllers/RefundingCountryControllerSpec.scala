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
import models.responses.{LatestApplicationResponse, LatestApplication, TraderKnownFactsResponse}
import java.time.LocalDateTime
import org.mockito.ArgumentMatchers.any
import play.api.test.Helpers.*
import play.api.inject.bind
import utils.CountryList
import pages.RefundingCountryNamePage
import play.api.mvc.Call

class RefundingCountryControllerSpec extends SpecBase with MockitoSugar {

  val onwardRoute: Call = Call("GET", "/foo")

  "RefundingCountry Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.RefundingCountryController.onPageLoad(models.NormalMode).url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[views.html.RefundingCountryView]
        val formProvider = application.injector.instanceOf[forms.RefundingCountryFormProvider]
        val countries: Seq[(String, String)] = CountryList.fromConfig(application.configuration)
        val allowed: Set[String] = countries.flatMap { case (n, c) => Seq(n, c) }.toSet
        val form = formProvider(allowed)

        status(result) mustEqual OK
        val body = contentAsString(result)
        val backUrl = application.configuration.get[String]("urls.loginContinue") + controllers.routes.TaskListDashboardController.onPageLoad().url
        body must not include s"href=\"$backUrl\""
        body mustEqual view(form, countries, controllers.routes.TaskListDashboardController.onPageLoad(), models.NormalMode)(request,
                                                                                                                             messages(application)
                                                                                                                            ).toString
      }
    }

    "must redirect to JourneyRecovery when no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, routes.RefundingCountryController.onPageLoad(models.NormalMode).url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must return OK and an empty form when arriving from the task list (Referer)" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.RefundingCountryController.onPageLoad(models.NormalMode).url)
          .withHeaders("Referer" -> controllers.routes.TaskListDashboardController.onPageLoad().url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[views.html.RefundingCountryView]
        val formProvider = application.injector.instanceOf[forms.RefundingCountryFormProvider]
        val countries: Seq[(String, String)] = CountryList.fromConfig(application.configuration)
        val allowed: Set[String] = countries.flatMap { case (n, c) => Seq(n, c) }.toSet
        val form = formProvider(allowed)

        status(result) mustEqual OK
        val body = contentAsString(result)
        val backUrl = application.configuration.get[String]("urls.loginContinue") + controllers.routes.TaskListDashboardController.onPageLoad().url
        body must not include s"href=\"$backUrl\""
        body mustEqual view(form, countries, controllers.routes.TaskListDashboardController.onPageLoad(), models.NormalMode)(request,
                                                                                                                             messages(application)
                                                                                                                            ).toString
      }
    }

    "must redirect to the next page when valid data is submitted" in {

      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "DE"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url

        verify(mockSessionRepository, times(1)).set(any())
      }
    }

      "must show duplicate application error when duplicate exists" in {
        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(scala.concurrent.Future.successful(TraderKnownFactsResponse(123, Some("ABC"), Some("49200"))))

        // with new rule: duplicate applies when applicationStatus == "D" or submissionStatus == null
        val sampleApp = LatestApplication(1L, "DE", LocalDateTime.now(), LocalDateTime.now(), "appNo", Some("D"), Some("R"), LocalDateTime.now())
        when(mockEuVatRefundsService.getLatestApplications(any())(any())).thenReturn(scala.concurrent.Future.successful(LatestApplicationResponse(List(sampleApp), 1)))

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[repositories.SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(("value", "DE"))

          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          contentAsString(result) must include(messages(application)("refundingCountry.error.duplicate"))
        }
      }

      "must escalate 5xx backend errors" in {
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(scala.concurrent.Future.successful(TraderKnownFactsResponse(123, Some("ABC"), Some("49200"))))
        when(mockEuVatRefundsService.getLatestApplications(any())(any())).thenReturn(scala.concurrent.Future.failed(new uk.gov.hmrc.http.UpstreamErrorResponse("boom", 500, 500, Map.empty)))

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(("value", "DE"))

          val result = route(application, request)
          whenReady(result.value.failed) { ex =>
            ex.getMessage must include("boom")
          }
        }
      }

      "must escalate 4xx backend errors" in {
        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(scala.concurrent.Future.successful(TraderKnownFactsResponse(123, Some("ABC"), Some("49200"))))
        when(mockEuVatRefundsService.getLatestApplications(any())(any())).thenReturn(scala.concurrent.Future.failed(new uk.gov.hmrc.http.UpstreamErrorResponse("boom400", 400, 400, Map.empty)))

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(("value", "DE"))

          val result = route(application, request)
          whenReady(result.value.failed) { ex =>
            ex.getMessage must include("boom400")
          }
        }
      }

      "must bypass validation when application status is A" in {
        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(scala.concurrent.Future.successful(TraderKnownFactsResponse(123, Some("ABC"), Some("49200"))))

        // under new rule, applicationStatus == D triggers validation (isDuplicate true), so to test bypass we use non-D and non-null submissionStatus
        val sampleApp = LatestApplication(2L, "DE", LocalDateTime.now(), LocalDateTime.now(), "appNo", Some("A"), Some("s"), LocalDateTime.now())
        when(mockEuVatRefundsService.getLatestApplications(any())(any())).thenReturn(scala.concurrent.Future.successful(LatestApplicationResponse(List(sampleApp), 1)))

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[repositories.SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(("value", "DE"))

          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
        }
      }

      "must treat submissionStatus 'S' case-insensitively and be treated as duplicate when applicationStatus is D" in {
        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

        when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any())).thenReturn(scala.concurrent.Future.successful(TraderKnownFactsResponse(123, Some("ABC"), Some("49200"))))

        val sampleApp = LatestApplication(3L, "DE", LocalDateTime.now(), LocalDateTime.now(), "appNo", Some("D"), Some("S"), LocalDateTime.now())
        when(mockEuVatRefundsService.getLatestApplications(any())(any())).thenReturn(scala.concurrent.Future.successful(LatestApplicationResponse(List(sampleApp), 1)))

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[repositories.SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(("value", "DE"))

          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          contentAsString(result) must include(messages(application)("refundingCountry.error.duplicate"))
        }
      }

    "must skip RefundingLanguage and set default language when country has single language" in {

      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "CZ"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.routes.RefundPeriodController.onPageLoad(models.NormalMode).url

        // verify session saved with language set
        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.RefundingLanguagePage).isDefined mustBe true
      }
    }

    "must clear previously stored language when country is changed" in {

      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      // Start with a saved country and language
      val starting = emptyUserAnswers
        .set(pages.RefundingCountryPage, "BG")
        .success
        .value
        .set(pages.RefundingLanguagePage, models.RefundingLanguage.Bulgarian)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(starting))
        .overrides(
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        // Submit a different country
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "DE"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.RefundingLanguagePage).isDefined mustBe false
      }
    }

    "must pre-fill the form when arriving from the task list and a saved value exists" in {

      val userAnswers = emptyUserAnswers.set(RefundingCountryNamePage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.RefundingCountryController.onPageLoad(models.NormalMode).url)
          .withHeaders("Referer" -> controllers.routes.TaskListDashboardController.onPageLoad().url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[views.html.RefundingCountryView]
        val formProvider = application.injector.instanceOf[forms.RefundingCountryFormProvider]
        val countries: Seq[(String, String)] = CountryList.fromConfig(application.configuration)
        val allowed: Set[String] = countries.flatMap { case (n, c) => Seq(n, c) }.toSet
        val form = formProvider(allowed).fill("DE")

        status(result) mustEqual OK
        val body = contentAsString(result)
        val backUrl = application.configuration.get[String]("urls.loginContinue") + controllers.routes.TaskListDashboardController.onPageLoad().url
        body must not include s"href=\"$backUrl\""
        body mustEqual view(form, countries, controllers.routes.TaskListDashboardController.onPageLoad(), models.NormalMode)(request,
                                                                                                                             messages(application)
                                                                                                                            ).toString
      }

    }

    "must pre-fill the form when arriving from the RefundingLanguage page and a saved value exists" in {

      val userAnswers = emptyUserAnswers.set(RefundingCountryNamePage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.RefundingCountryController.onPageLoad(models.NormalMode).url)
          .withHeaders("Referer" -> controllers.routes.RefundingLanguageController.onPageLoad(models.NormalMode).url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[views.html.RefundingCountryView]
        val formProvider = application.injector.instanceOf[forms.RefundingCountryFormProvider]
        val countries: Seq[(String, String)] = CountryList.fromConfig(application.configuration)
        val allowed: Set[String] = countries.flatMap { case (n, c) => Seq(n, c) }.toSet
        val form = formProvider(allowed).fill("DE")

        status(result) mustEqual OK
        val body = contentAsString(result)
        val backUrl = application.configuration.get[String]("urls.loginContinue") + controllers.routes.TaskListDashboardController.onPageLoad().url
        body must not include s"href=\"$backUrl\""
        body mustEqual view(form, countries, controllers.routes.TaskListDashboardController.onPageLoad(), models.NormalMode)(request,
                                                                                                                             messages(application)
                                                                                                                            ).toString
      }
    }

    "must set CountryChangedPage to true when country is changed in CheckMode" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val starting = emptyUserAnswers
        .set(pages.RefundingCountryPage, "BG")
        .success
        .value
        .set(pages.RefundingLanguagePage, models.RefundingLanguage.Bulgarian)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(starting))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.CheckMode).url)
          .withFormUrlEncodedBody(("value", "DE"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.CountryChangedPage) mustBe Some(true)
      }
    }

    "must clear currency when country is changed" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val starting = emptyUserAnswers
        .set(pages.RefundingCountryPage, "BG")
        .success
        .value
        .set(pages.RefundingCurrencyPage, "BGN")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(starting))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "EE"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.RefundingCurrencyPage).isDefined mustBe false
      }
    }

    "must auto-set currency when country has single language and single currency" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "CZ"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.RefundingCurrencyPage) mustBe Some("CZK")
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", ""))

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST

        val body = contentAsString(result)
        body must include(messages(application)("refundingCountry.error.required"))
        body must include(messages(application)("refundingCountry.error.summary"))

        // typed-but-unmatched input should show invalid message
        val typedRequest = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", ""), ("valueTyped", "NotACountry"))

        val typedResult = route(application, typedRequest).value
        status(typedResult) mustEqual BAD_REQUEST
        val typedBody = contentAsString(typedResult)
        typedBody must include(messages(application)("refundingCountry.error.invalid"))
        typedBody must include(messages(application)("refundingCountry.error.invalid.summary"))

        // non-existent code should also show invalid message
        val rawInvalidRequest = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "ZZ"))

        val rawInvalidResult = route(application, rawInvalidRequest).value
        status(rawInvalidResult) mustEqual BAD_REQUEST
        val rawInvalidBody = contentAsString(rawInvalidResult)
        rawInvalidBody must include(messages(application)("refundingCountry.error.invalid"))
        rawInvalidBody must include(messages(application)("refundingCountry.error.invalid.summary"))
      }
    }
  }
}
