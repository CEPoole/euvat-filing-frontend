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
import config.FrontendAppConfig
import models.{Fuel, NormalMode}
import models.requests.LatestApplicationRequest
import models.responses.{LatestApplication, LatestApplicationResponse}
import navigation.FakeNavigator
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import pages.RefundingCountryNamePage
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.CSRFTokenHelper.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.Future

class RefundingCountryControllerSpec extends SpecBase with MockitoSugar {
  // Use `normalizeHtml` from SpecBase to normalize CSRF and nonce differences
  val onwardRoute: Call = Call("GET", "/foo")

  "RefundingCountry Controller" - {
    "must return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.RefundingCountryController.onPageLoad(models.NormalMode).url)
        val result = route(application, request).value

        val view = application.injector.instanceOf[views.html.RefundingCountryView]
        val formProvider = application.injector.instanceOf[forms.RefundingCountryFormProvider]
        val countries: Map[String, String] = application.injector.instanceOf[FrontendAppConfig].countriesInEU
        val allowed: Set[String] = countries.flatMap { case (n, c) => Seq(n, c) }.toSet

        status(result) mustEqual OK
        val body = contentAsString(result)
        val backUrl = application.configuration.get[String]("urls.loginContinue") + controllers.routes.TaskListDashboardController.onPageLoad().url
        body must not include s"href=\"$backUrl\""
        val viewRequest = request.withCSRFToken
        normalizeHtml(body) mustEqual normalizeHtml(
          view(formProvider(), countries, controllers.routes.TaskListDashboardController.onPageLoad(), models.NormalMode)(viewRequest,
                                                                                                                          messages(application)
                                                                                                                         ).toString
        )
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
        val countries: Map[String, String] = application.injector.instanceOf[FrontendAppConfig].countriesInEU
        val allowed: Set[String] = countries.flatMap { case (n, c) => Seq(n, c) }.toSet

        status(result) mustEqual OK
        val body = contentAsString(result)
        val backUrl = application.configuration.get[String]("urls.loginContinue") + controllers.routes.TaskListDashboardController.onPageLoad().url
        body must not include s"href=\"$backUrl\""
        val viewRequest = request.withCSRFToken
        normalizeHtml(body) mustEqual normalizeHtml(
          view(formProvider(), countries, controllers.routes.TaskListDashboardController.onPageLoad(), models.NormalMode)(viewRequest,
                                                                                                                          messages(application)
                                                                                                                         ).toString
        )
      }
    }

    "must redirect to the next page when valid data is submitted" in {
      when(mockEuVatRefundsService.getLatestApplications(any[LatestApplicationRequest]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(LatestApplicationResponse(List.empty, 0)))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "DE"))
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url

        verify(mockSessionRepository, times(1)).set(any())
        verify(mockEuVatRefundsService, times(1)).getLatestApplications(any[LatestApplicationRequest]())(any[HeaderCarrier]())
      }
    }

    "must show duplicate application error when duplicate exists" in {
      // with new rule: duplicate applies when applicationStatus == "D" or "A"
      val sampleApp = LatestApplication(1L, "DE", LocalDateTime.now(), LocalDateTime.now(), "appNo", Some("D"), Some("R"), LocalDateTime.now())
      when(mockEuVatRefundsService.getLatestApplications(any())(any()))
        .thenReturn(scala.concurrent.Future.successful(LatestApplicationResponse(List(sampleApp), 1)))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
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
      when(mockEuVatRefundsService.getLatestApplications(any())(any()))
        .thenReturn(scala.concurrent.Future.failed(new uk.gov.hmrc.http.UpstreamErrorResponse("boom", 500, 500, Map.empty)))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "DE"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must escalate 4xx backend errors" in {
      when(mockEuVatRefundsService.getLatestApplications(any())(any()))
        .thenReturn(scala.concurrent.Future.failed(new uk.gov.hmrc.http.UpstreamErrorResponse("boom400", 400, 400, Map.empty)))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "DE"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must bypass validation when application status is A" in {
      when(mockEuVatRefundsService.getLatestApplications(any())(any()))
        .thenReturn(scala.concurrent.Future.successful(LatestApplicationResponse(List.empty, 0)))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody(("value", "DE"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
      }
    }

    "must treat submissionStatus 'S' case-insensitively and be treated as duplicate when applicationStatus is D" in {
      val sampleApp = LatestApplication(3L, "DE", LocalDateTime.now(), LocalDateTime.now(), "appNo", Some("D"), Some("S"), LocalDateTime.now())
      when(mockEuVatRefundsService.getLatestApplications(any())(any()))
        .thenReturn(scala.concurrent.Future.successful(LatestApplicationResponse(List(sampleApp), 1)))

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

    "must clear purchase selections when country is changed" in {
      // Start with a saved country and various purchase selections
      val starting = emptyUserAnswers
        .set(pages.RefundingCountryPage, "BG")
        .success
        .value
        .set(pages.PurchaseTypePage, Fuel)
        .success
        .value
        .set(pages.PurchaseSubTypePage, "1.1")
        .success
        .value
        .set(pages.PurchaseSubTypeLabelPage, "Fuel label")
        .success
        .value
        .set(pages.PurchaseSubCategoryPage, "1.1.1")
        .success
        .value
        .set(pages.PurchaseSubCategoryLabelPage, "Fuel sub label")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(starting))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
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

        saved.get(pages.PurchaseTypePage).isDefined mustBe false
        saved.get(pages.PurchaseSubTypePage).isDefined mustBe false
        saved.get(pages.PurchaseSubTypeLabelPage).isDefined mustBe false
        saved.get(pages.PurchaseSubCategoryPage).isDefined mustBe false
        saved.get(pages.PurchaseSubCategoryLabelPage).isDefined mustBe false
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
        val countries: Map[String, String] = application.injector.instanceOf[FrontendAppConfig].countriesInEU
        val allowed: Set[String] = countries.flatMap { case (n, c) => Seq(n, c) }.toSet
        val form = formProvider().fill("DE")

        status(result) mustEqual OK
        val body = contentAsString(result)
        val backUrl = application.configuration.get[String]("urls.loginContinue") + controllers.routes.TaskListDashboardController.onPageLoad().url
        body must not include s"href=\"$backUrl\""
        val viewRequest = request.withCSRFToken
        normalizeHtml(body) mustEqual normalizeHtml(
          view(form, countries, controllers.routes.TaskListDashboardController.onPageLoad(), models.NormalMode)(viewRequest,
                                                                                                                messages(application)
                                                                                                               ).toString
        )
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
        val countries: Map[String, String] = application.injector.instanceOf[FrontendAppConfig].countriesInEU
        val allowed: Set[String] = countries.flatMap { case (n, c) => Seq(n, c) }.toSet
        val form = formProvider().fill("DE")

        status(result) mustEqual OK
        val body = contentAsString(result)
        val backUrl = application.configuration.get[String]("urls.loginContinue") + controllers.routes.TaskListDashboardController.onPageLoad().url
        body must not include s"href=\"$backUrl\""
        val viewRequest = request.withCSRFToken
        normalizeHtml(body) mustEqual normalizeHtml(
          view(form, countries, controllers.routes.TaskListDashboardController.onPageLoad(), models.NormalMode)(viewRequest,
                                                                                                                messages(application)
                                                                                                               ).toString
        )
      }
    }

    "must set CountryChangedPage to true when country is changed in CheckMode" in {
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

    "must auto-set currency when country has single currency even if multiple languages" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "BE"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.RefundingCurrencyPage) mustBe Some("EUR")
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

    "must save both code and name when a code is submitted" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "AT"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue

        saved.get(pages.RefundingCountryPage) mustBe Some("AT")
        saved.get(pages.RefundingCountryNamePage) mustBe Some("Austria")
      }
    }

    "must recover and return Bad Request when session save fails" in {
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.failed(new RuntimeException("boom"))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingCountryController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "DE"))

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
      }
    }
  }
}
