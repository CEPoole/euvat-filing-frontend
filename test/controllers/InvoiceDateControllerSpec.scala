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
import models.*
import navigation.FakeNavigator
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatestplus.mockito.MockitoSugar
import pages.RefundPeriodPage
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import java.time.{LocalDate, LocalDateTime}

class InvoiceDateControllerSpec extends SpecBase with MockitoSugar {

  val onwardRoute = Call("GET", "/foo")

  "InvoiceDate Controller" - {

    ".onPageLoad" - {

      "must return OK and the correct view for a GET when refund period exists" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, routes.InvoiceDateController.onPageLoad(models.NormalMode).url)
          val result = route(application, request).value
          val view = application.injector.instanceOf[views.html.InvoiceDateView]
          implicit val msgs = messages(application)

          status(result) mustEqual OK
          normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(application.injector.instanceOf[forms.InvoiceDateFormProvider].apply(),
                                                 models.NormalMode,
                                                 routes.InvoiceNumberController.onPageLoad(models.NormalMode)
                                                )(request, msgs).toString)
        }
      }

      "must populate the view correctly on a GET when the question has previously been answered" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers
          .set(RefundPeriodPage, savedPeriod)
          .success
          .value
          .set(pages.InvoiceDatePage, LocalDate.of(2025, 4, 15))
          .success
          .value

        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(GET, routes.InvoiceDateController.onPageLoad(models.NormalMode).url)
          val result = route(application, request).value
          val view = application.injector.instanceOf[views.html.InvoiceDateView]
          implicit val msgs = messages(application)

          status(result) mustEqual OK
          normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(
            application.injector.instanceOf[forms.InvoiceDateFormProvider].apply().fill(LocalDate.of(2025, 4, 15)),
            models.NormalMode,
            routes.InvoiceNumberController.onPageLoad(models.NormalMode)
          )(request, msgs).toString)
        }
      }

      "must redirect to journey recovery when no user answers exist" in {
        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val request = FakeRequest(GET, routes.InvoiceDateController.onPageLoad(models.NormalMode).url)
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
        }
      }
    }

    ".onSubmit" - {

      "must redirect to the next page when valid date within refund period submitted" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

        val application = applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[navigation.Navigator].toInstance(new FakeNavigator(onwardRoute)),
            bind[repositories.SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "value.day"   -> "15",
              "value.month" -> "04",
              "value.year"  -> "2025"
            )
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual onwardRoute.url
          verify(mockSessionRepository).set(any())
        }
      }

      "must return Bad Request when date is in the future" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2024, 1, 1, 0, 0), LocalDateTime.of(2025, 12, 31, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val future = LocalDate.now().plusDays(1)
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "value.day"   -> f"${future.getDayOfMonth}%02d",
              "value.month" -> f"${future.getMonthValue}%02d",
              "value.year"  -> future.getYear.toString
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val body = contentAsString(result)
          body must include(messages(application)("invoiceDate.error.past"))
          body must include("href=\"#value.day\"")
        }
      }

      /* TODO: commented out, please see InvoiceDateController for details on when this should be added back in

      "must return Bad Request when date is outside refund period" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "value.day" -> "15",
              "value.month" -> "09",
              "value.year" -> "2025"
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val body = contentAsString(result)
          body must include(messages(application)("invoiceDate.error.outsideRefundPeriod"))
          body must include("href=\"#value.day\"")
        }
      }
       */

      "must redirect to journey recovery when refund period missing on submit" in {
        val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "value.day"   -> "15",
              "value.month" -> "04",
              "value.year"  -> "2025"
            )
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
        }
      }

      "must return Bad Request and link to month when month is missing" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "value.day"   -> "15",
              "value.month" -> "",
              "value.year"  -> "2025"
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val body = contentAsString(result)
          val expected = messages(application)("invoiceDate.error.required", messages(application)("date.error.month"))
          body must include(expected)
          body must include("href=\"#value.month\"")
          // entered day and year should be preserved
          body must include("value=\"15\"")
          body must include("value=\"2025\"")
        }
      }

      "must return Bad Request and link to year when year is missing" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "value.day"   -> "15",
              "value.month" -> "04",
              "value.year"  -> ""
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val body = contentAsString(result)
          val expected = messages(application)("invoiceDate.error.required", messages(application)("date.error.year"))
          body must include(expected)
          body must include("href=\"#value.year\"")
          // entered day and month should be preserved
          body must include("value=\"15\"")
          body must include("value=\"04\"")
        }
      }

      "must return Bad Request and link to day when day and month are missing" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "value.day"   -> "",
              "value.month" -> "",
              "value.year"  -> "2025"
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val body = contentAsString(result)
          val expected = messages(application)("invoiceDate.error.required.two",
                                               messages(application)("date.error.day"),
                                               messages(application)("date.error.month")
                                              )
          body must include(expected)
          body must include("href=\"#value.day\"")
          // entered year should be preserved
          body must include("value=\"2025\"")
        }
      }

      "must return Bad Request and link to day when all fields are missing" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "value.day"   -> "",
              "value.month" -> "",
              "value.year"  -> ""
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val body = contentAsString(result)
          val expected = messages(application)("invoiceDate.error.required.all")
          body must include(expected)
          body must include("href=\"#value.day\"")
        }
      }

      "must return Bad Request and link to day when numeric garbage input posted" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "value.day"   -> "123",
              "value.month" -> "123",
              "value.year"  -> "1234"
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val body = contentAsString(result)
          val expected =
            messages(application)("invoiceDate.error.invalid.two", messages(application)("date.error.day"), messages(application)("date.error.month"))
          body must include(expected)
          body must include("href=\"#value.day\"")
        }
      }

      "must return Bad Request and link to day when day and month are invalid text" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "value.day"   -> "abc",
              "value.month" -> "def",
              "value.year"  -> "2025"
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val body = contentAsString(result)
          val expected =
            messages(application)("invoiceDate.error.invalid.two", messages(application)("date.error.day"), messages(application)("date.error.month"))
          body must include(expected)
          body must include("href=\"#value.day\"")
        }
      }

      "must return Bad Request and link to month when day is valid but month is invalid text" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.NormalMode).url)
            .withFormUrlEncodedBody(
              "value.day"   -> "31",
              "value.month" -> "abc",
              "value.year"  -> "2026"
            )
          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          val body = contentAsString(result)
          val expected = messages(application)("invoiceDate.error.invalid.month")
          body must include(expected)
          body must include("href=\"#value.month\"")
        }
      }

      "must redirect to Check Your Answers when in CheckMode" in {
        val savedPeriod = models.RefundPeriod(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 8, 1, 23, 59))
        val userAnswers = emptyUserAnswers.set(RefundPeriodPage, savedPeriod).success.value
        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

        val checkYourAnswersRoute = controllers.routes.CheckYourClaimDetailsController.onPageLoad()

        val application = applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[navigation.Navigator].toInstance(new FakeNavigator(checkYourAnswersRoute)),
            bind[repositories.SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.InvoiceDateController.onSubmit(models.CheckMode).url)
            .withFormUrlEncodedBody(
              "value.day"   -> "15",
              "value.month" -> "04",
              "value.year"  -> "2025"
            )

          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual checkYourAnswersRoute.url
          verify(mockSessionRepository).set(any())
        }
      }
    }
  }
}
