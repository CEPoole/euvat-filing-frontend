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
import forms.ContactDetailsFormProvider
import models.{CheckMode, ContactDetails, NormalMode, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.ContactDetailsPage
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.ContactDetailsView

import scala.concurrent.Future

class ContactDetailsControllerSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  private def onwardRoute = Call("GET", "/foo")
  private def backUrl: Call = routes.RefundPeriodController.onPageLoad(NormalMode)

  private val formProvider = new ContactDetailsFormProvider()
  private val form = formProvider()

  private lazy val contactDetailsNormalRoute = routes.ContactDetailsController.onPageLoad(NormalMode).url
  private lazy val contactDetailsCheckRoute = routes.ContactDetailsController.onPageLoad(CheckMode).url

  private val validFormData = Map(
    "contactEmail"     -> "test@example.com",
    "contactTelephone" -> "07700900000"
  )

  private val contactDetails = ContactDetails(
    email     = "test@example.com",
    telephone = Some("07700900000")
  )

  private val mockSessionRepository: SessionRepository = mock[SessionRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSessionRepository)
  }

  private def applicationWithMockedRepo(userAnswers: Option[UserAnswers]) =
    applicationBuilder(userAnswers = userAnswers)
      .overrides(
        bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
        bind[SessionRepository].toInstance(mockSessionRepository)
      )
      .build()

  "ContactDetailsController" - {

    "onPageLoad (NormalMode)" - {

      "must return OK and render the view with an empty form when no existing data" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, contactDetailsNormalRoute)
          val result = route(application, request).value
          val view = application.injector.instanceOf[ContactDetailsView]

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(form, NormalMode, backUrl)(request, messages(application)).toString
        }
      }

      "must pre-populate the form when existing data is present" in {
        val userAnswers = emptyUserAnswers.set(ContactDetailsPage, contactDetails).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, contactDetailsNormalRoute)
          val result = route(application, request).value
          val view = application.injector.instanceOf[ContactDetailsView]

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(form.fill(contactDetails), NormalMode, backUrl)(request, messages(application)).toString
        }
      }

      "must redirect to Journey Recovery when no user answers exist" in {
        val application = applicationWithMockedRepo(userAnswers = None)

        running(application) {
          val request = FakeRequest(GET, contactDetailsNormalRoute)
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
          verify(mockSessionRepository, never).set(any())
        }
      }
    }

    "onPageLoad (CheckMode)" - {

      "must return OK and pre-populate the form with existing data" in {
        val userAnswers = emptyUserAnswers.set(ContactDetailsPage, contactDetails).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, contactDetailsCheckRoute)
          val result = route(application, request).value

          status(result) mustEqual OK

          val body = contentAsString(result)
          body must include("test@example.com")
          body must include("07700900000")
        }
      }
    }

    "onSubmit (NormalMode)" - {

      "must save data and redirect to the next page on valid submission" in {
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val application = applicationWithMockedRepo(Some(emptyUserAnswers))

        running(application) {
          val request = FakeRequest(POST, contactDetailsNormalRoute).withFormUrlEncodedBody(validFormData.toSeq*)
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual onwardRoute.url
          verify(mockSessionRepository, times(1)).set(any())
        }
      }

      "must return BAD_REQUEST and not persist when email is missing" in {
        val application = applicationWithMockedRepo(Some(emptyUserAnswers))

        running(application) {
          val request = FakeRequest(POST, contactDetailsNormalRoute).withFormUrlEncodedBody("contactEmail" -> "")
          val result = route(application, request).value
          val view = application.injector.instanceOf[ContactDetailsView]

          status(result) mustEqual BAD_REQUEST
          contentAsString(result) mustEqual view(
            form.bind(Map("contactEmail" -> "")),
            NormalMode,
            backUrl
          )(request, messages(application)).toString
          verify(mockSessionRepository, never).set(any())
        }
      }

      "must return BAD_REQUEST and not persist when email format is invalid" in {
        val application = applicationWithMockedRepo(Some(emptyUserAnswers))

        running(application) {
          val request = FakeRequest(POST, contactDetailsNormalRoute)
            .withFormUrlEncodedBody(validFormData.updated("contactEmail", "not-an-email").toSeq*)
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          verify(mockSessionRepository, never).set(any())
        }
      }

      "must redirect to Journey Recovery and not persist when no user answers exist" in {
        val application = applicationWithMockedRepo(userAnswers = None)

        running(application) {
          val request = FakeRequest(POST, contactDetailsNormalRoute).withFormUrlEncodedBody(validFormData.toSeq*)
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
          verify(mockSessionRepository, never).set(any())
        }
      }
    }

    "onSubmit (CheckMode)" - {

      "must save data and redirect on valid submission" in {
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val application = applicationWithMockedRepo(Some(emptyUserAnswers))

        running(application) {
          val request = FakeRequest(POST, contactDetailsCheckRoute).withFormUrlEncodedBody(validFormData.toSeq*)
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual onwardRoute.url
          verify(mockSessionRepository, times(1)).set(any())
        }
      }

      "must set ClaimDetailsAmendedPage to true when contact details are changed and ClaimDetailsCompletedPage is true" in {
        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val ua = emptyUserAnswers
          .set(pages.ContactDetailsPage, models.ContactDetails("existing@email.com", None))
          .success
          .value
          .set(pages.ClaimDetailsCompletedPage, true)
          .success
          .value

        val application = applicationBuilder(userAnswers = Some(ua))
          .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.ContactDetailsController.onSubmit(models.CheckMode).url)
            .withFormUrlEncodedBody(
              "contactEmail"     -> "new@email.com",
              "contactTelephone" -> ""
            )
          val result = route(application, request).value
          status(result) mustEqual SEE_OTHER

          import org.mockito.ArgumentCaptor
          val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
          verify(mockSessionRepository, times(1)).set(captor.capture())
          captor.getValue.get(pages.ClaimDetailsAmendedPage) mustBe Some(true)
        }
      }

      "must NOT set ClaimDetailsAmendedPage when contact details are unchanged" in {
        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val ua = emptyUserAnswers
          .set(pages.ContactDetailsPage, models.ContactDetails("test@email.com", None))
          .success
          .value
          .set(pages.ClaimDetailsCompletedPage, true)
          .success
          .value

        val application = applicationBuilder(userAnswers = Some(ua))
          .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.ContactDetailsController.onSubmit(models.CheckMode).url)
            .withFormUrlEncodedBody(
              "contactEmail"     -> "test@email.com",
              "contactTelephone" -> ""
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
        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[repositories.SessionRepository].toInstance(mockSessionRepository))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.ContactDetailsController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "contactEmail"     -> "test@email.com",
              "contactTelephone" -> ""
            )
          val result = route(application, request).value
          status(result) mustEqual SEE_OTHER

          import org.mockito.ArgumentCaptor
          val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
          verify(mockSessionRepository, times(1)).set(captor.capture())
          captor.getValue.get(pages.ClaimDetailsAmendedPage).isDefined mustBe false
        }
      }
    }
  }
}
