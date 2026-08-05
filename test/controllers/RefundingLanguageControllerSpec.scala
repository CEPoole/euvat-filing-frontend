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
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.inject.bind
import play.api.Configuration
import com.typesafe.config.ConfigFactory
import repositories.SessionRepository
import pages.{RefundingCountryNamePage, RefundingCountryPage, RefundingLanguagePage}
import play.api.mvc.Call
import utils.ConfigLanguageMapping

import scala.concurrent.Future

class RefundingLanguageControllerSpec extends SpecBase with MockitoSugar {

  val onwardRoute: Call = Call("GET", "/foo")

  "RefundingLanguage Controller" - {

    "must return OK and the correct view for a GET when country present" in {

      val userAnswers = emptyUserAnswers.set(RefundingCountryPage, "AT").success.value

      val cfg = Configuration(ConfigFactory.parseString("language.mapping.AT=[\"german\", \"english\"]"))
      val mappingSvc = new ConfigLanguageMapping(cfg)

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigLanguageMapping].toInstance(mappingSvc)
        )
        .build()

      running(application) {
        val request = FakeRequest(GET, routes.RefundingLanguageController.onPageLoad(models.NormalMode).url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[views.html.RefundingLanguageView]
        val formProvider = application.injector.instanceOf[forms.RefundingLanguageFormProvider]
        val form = formProvider()
        val langs = mappingSvc.languagesFor("AT")
        val msgs = messages(application)
        val items = langs.zipWithIndex.flatMap { case (lang, idx) =>
          _root_.models.RefundingLanguage.values.find(_.toString.equalsIgnoreCase(lang)).map { v =>
            uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem(
              content = uk.gov.hmrc.govukfrontend.views.Aliases.Text(msgs(s"refundingLanguage.${v.toString}")),
              value   = Some(v.toString),
              id      = Some(s"value_$idx")
            )
          }
        }

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, items, routes.RefundingCountryController.onPageLoad(models.NormalMode), models.NormalMode)(
          request,
          messages(application)
        ).toString)

        // also supports CheckMode change route
        val changeRequest = FakeRequest(GET, routes.RefundingLanguageController.onPageLoad(models.CheckMode).url)
        val changeResult = route(application, changeRequest).value

        status(changeResult) mustEqual OK
        normalizeHtml(contentAsString(changeResult)) mustEqual normalizeHtml(view(form, items, routes.RefundingCountryController.onPageLoad(models.CheckMode), models.CheckMode)(
          changeRequest,
          messages(application)
        ).toString)
      }
    }

    "must redirect to JourneyRecovery when no country present" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.RefundingLanguageController.onPageLoad(models.NormalMode).url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to next page when valid data submitted" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val userAnswers = emptyUserAnswers.set(RefundingCountryNamePage, "AT").success.value

      val cfg = Configuration(ConfigFactory.parseString("language.mapping.AT=[\"german\", \"english\"]"))
      val mappingSvc = new ConfigLanguageMapping(cfg)

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[navigation.Navigator].toInstance(new navigation.FakeNavigator(onwardRoute)),
          bind[repositories.SessionRepository].toInstance(mockSessionRepository),
          bind[ConfigLanguageMapping].toInstance(mappingSvc)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingLanguageController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "english"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url

        verify(mockSessionRepository).set(any())

        // also supports CheckMode submission
        val checkRequest = FakeRequest(POST, routes.RefundingLanguageController.onSubmit(models.CheckMode).url)
          .withFormUrlEncodedBody(("value", "english"))

        val checkResult = route(application, checkRequest).value
        status(checkResult) mustEqual SEE_OTHER
        redirectLocation(checkResult).value mustEqual onwardRoute.url
      }
    }

    "must return Bad Request when invalid data submitted" in {

      val userAnswers = emptyUserAnswers.set(RefundingCountryNamePage, "AT").success.value

      val cfg = Configuration(ConfigFactory.parseString("language.mapping.AT=[\"german\", \"english\"]"))
      val mappingSvc = new ConfigLanguageMapping(cfg)

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigLanguageMapping].toInstance(mappingSvc))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingLanguageController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", ""))

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include(messages(application)("refundingLanguage.error.required"))
      }
    }

    "must return OK and the correct view for a GET when country stored as delimited name+code" in {

      val userAnswers = emptyUserAnswers.set(RefundingCountryNamePage, "AT,Austria").success.value

      val cfg = Configuration(ConfigFactory.parseString("language.mapping.AT=[\"german\", \"english\"]"))
      val mappingSvc = new ConfigLanguageMapping(cfg)

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigLanguageMapping].toInstance(mappingSvc)
        )
        .build()

      running(application) {
        val request = FakeRequest(GET, routes.RefundingLanguageController.onPageLoad(models.NormalMode).url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[views.html.RefundingLanguageView]
        val formProvider = application.injector.instanceOf[forms.RefundingLanguageFormProvider]
        val form = formProvider()
        val langs = mappingSvc.languagesFor("AT")
        val msgs = messages(application)
        val items = langs.zipWithIndex.flatMap { case (lang, idx) =>
          _root_.models.RefundingLanguage.values.find(_.toString.equalsIgnoreCase(lang)).map { v =>
            uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem(
              content = uk.gov.hmrc.govukfrontend.views.Aliases.Text(msgs(s"refundingLanguage.${v.toString}")),
              value   = Some(v.toString),
              id      = Some(s"value_$idx")
            )
          }
        }

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, items, routes.RefundingCountryController.onPageLoad(models.NormalMode), models.NormalMode)(
          request,
          messages(application)
        ).toString)
      }
    }

    "must set ClaimDetailsAmendedPage to true when language is changed and ClaimDetailsCompletedPage is true" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(pages.RefundingCountryPage, "BE")
        .success
        .value
        .set(pages.RefundingLanguagePage, models.RefundingLanguage.French)
        .success
        .value
        .set(pages.ClaimDetailsCompletedPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingLanguageController.onSubmit(models.CheckMode).url)
          .withFormUrlEncodedBody(("value", "english"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        captor.getValue.get(pages.ClaimDetailsAmendedPage) mustBe Some(true)
      }
    }

    "must NOT set ClaimDetailsAmendedPage when language is unchanged" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(pages.RefundingCountryPage, "BE")
        .success
        .value
        .set(pages.RefundingLanguagePage, models.RefundingLanguage.English)
        .success
        .value
        .set(pages.ClaimDetailsCompletedPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingLanguageController.onSubmit(models.CheckMode).url)
          .withFormUrlEncodedBody(("value", "english"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        captor.getValue.get(pages.ClaimDetailsAmendedPage).isDefined mustBe false
      }
    }

    "must NOT set ClaimDetailsAmendedPage when ClaimDetailsCompletedPage is not set" in {
      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val ua = emptyUserAnswers
        .set(pages.RefundingCountryPage, "BE")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(ua))
        .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.RefundingLanguageController.onSubmit(models.NormalMode).url)
          .withFormUrlEncodedBody(("value", "english"))

        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER

        import org.mockito.ArgumentCaptor
        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        captor.getValue.get(pages.ClaimDetailsAmendedPage).isDefined mustBe false
      }
    }
  }

  "must redirect to JourneyRecovery on submit when no country present" in {

    val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

    running(application) {
      val request = FakeRequest(POST, routes.RefundingLanguageController.onSubmit(models.NormalMode).url)
        .withFormUrlEncodedBody(("value", ""))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual controllers.routes.JourneyRecoveryController.onPageLoad().url
    }
  }
}
