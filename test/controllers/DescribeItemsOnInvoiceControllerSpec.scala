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
import forms.DescribeItemsOnInvoiceFormProvider
import models.{CheckMode, NormalMode, UserAnswers, PurchaseType, Fuel, Other}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatestplus.mockito.MockitoSugar
import pages.DescribeItemsOnInvoicePage
import play.api.inject.bind
import navigation.{FakeNavigator, Navigator}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import utils.ConfigPurchaseMapping
import views.html.DescribeItemsOnInvoiceView

import scala.concurrent.Future

class DescribeItemsOnInvoiceControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute = Call("GET", "/foo")

  lazy val describeItemsOnInvoiceRoute = routes.DescribeItemsOnInvoiceController.onPageLoad(NormalMode).url
  lazy val describeItemsOnInvoiceCheckModeRoute = routes.DescribeItemsOnInvoiceController.onPageLoad(CheckMode).url

  val formProvider = new DescribeItemsOnInvoiceFormProvider()
  val form = formProvider()

  "DescribeItemsOnInvoice Controller" - {

    "must return OK and the correct view for a GET" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("10.6", "purchase.sub.other.6"), ("10.99", "purchase.sub.other.99"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig)).build()

      running(application) {
        val request = FakeRequest(GET, describeItemsOnInvoiceRoute)
        val result = route(application, request).value
        val view = application.injector.instanceOf[DescribeItemsOnInvoiceView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, routes.PurchaseTypeController.onPageLoad(NormalMode))(request,
                                                                                                                       messages(application)
                                                                                                                      ).toString
      }
    }

    "must short-circuit to purchase CYA in CheckMode when value unchanged" in {
      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val userAnswers = UserAnswers(userAnswersId).set(DescribeItemsOnInvoicePage, "Fuel and transport costs").success.value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val controller = application.injector.instanceOf[DescribeItemsOnInvoiceController]
        val postRequest = FakeRequest(POST, "/").withFormUrlEncodedBody(("value", "Fuel and transport costs"))
        val result = controller.onSubmit(models.CheckMode).apply(postRequest)

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
        org.mockito.Mockito.verify(mockSessionRepository, org.mockito.Mockito.times(0)).set(any())
      }
    }

    "must persist and redirect to next page in CheckMode when value changed" in {
      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val userAnswers = emptyUserAnswers

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository),
            bind[Navigator].toInstance(new FakeNavigator(onwardRoute))
          )
          .build()

      running(application) {
        val controller = application.injector.instanceOf[DescribeItemsOnInvoiceController]
        val postRequest = FakeRequest(POST, "/").withFormUrlEncodedBody(("value", "Fuel and transport costs"))
        val result = controller.onSubmit(models.CheckMode).apply(postRequest)

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual onwardRoute.url
        org.mockito.Mockito.verify(mockSessionRepository, org.mockito.Mockito.times(1)).set(any())
      }
    }
    "must populate the view correctly on a GET when the question has previously been answered" in {
      val userAnswers = UserAnswers(userAnswersId).set(DescribeItemsOnInvoicePage, "Fuel and transport costs").success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, describeItemsOnInvoiceRoute)
        val view = application.injector.instanceOf[DescribeItemsOnInvoiceView]
        val result = route(application, request).value

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form.fill("Fuel and transport costs"),
                                               NormalMode,
                                               routes.PurchaseTypeController.onPageLoad(NormalMode)
                                              )(request, messages(application)).toString
      }
    }

    "must show backlink to PurchaseSubCategory when PurchaseSubCategoryPage present but PurchaseSubTypePage missing" in {
      val child = "1.2"
      val userAnswers = emptyUserAnswers
        .set(pages.PurchaseTypePage, Fuel)
        .success
        .value
        .set(pages.PurchaseSubCategoryPage, child)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, describeItemsOnInvoiceRoute)
        val view = application.injector.instanceOf[DescribeItemsOnInvoiceView]
        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, Call("GET", "/file-eu-vat/fuel-type-or-vehicle"))(request, messages(application)).toString
        )
      }
    }

    "must redirect to the next page when valid data is submitted" in {
      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(POST, describeItemsOnInvoiceRoute)
          .withFormUrlEncodedBody(("value", "Fuel and transport costs"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.InvoiceTypeController.onPageLoad(NormalMode).url
      }
    }

    "must return a Bad Request and errors when data exceeding the max length is submitted" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val tooLong = "a" * 256

        val request =
          FakeRequest(POST, describeItemsOnInvoiceRoute)
            .withFormUrlEncodedBody(("value", tooLong))

        val boundForm = form.bind(Map("value" -> tooLong))
        val view = application.injector.instanceOf[DescribeItemsOnInvoiceView]
        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) mustEqual view(boundForm, NormalMode, routes.PurchaseTypeController.onPageLoad(NormalMode))(request,
                                                                                                                            messages(application)
                                                                                                                           ).toString
      }
    }

    "must redirect to PurchaseWarningController and persist an empty value when empty data is submitted in NormalMode" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, describeItemsOnInvoiceRoute)
            .withFormUrlEncodedBody(("value", ""))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.PurchaseWarningController.onPageLoad(NormalMode).url

        val captor = ArgumentCaptor.forClass(classOf[UserAnswers])
        Mockito.verify(mockSessionRepository).set(captor.capture())
        captor.getValue.get(DescribeItemsOnInvoicePage).value mustEqual ""
      }
    }

    "must redirect to PurchaseWarningController and persist an empty value when empty data is submitted in CheckMode" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)
      val userAnswers = UserAnswers(userAnswersId).set(DescribeItemsOnInvoicePage, "Fuel and transport costs").success.value

      val application =
        applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, describeItemsOnInvoiceCheckModeRoute)
            .withFormUrlEncodedBody(("value", ""))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.PurchaseWarningController.onPageLoad(CheckMode).url

        val captor = ArgumentCaptor.forClass(classOf[UserAnswers])
        Mockito.verify(mockSessionRepository).set(captor.capture())
        captor.getValue.get(DescribeItemsOnInvoicePage).value mustEqual ""
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, describeItemsOnInvoiceRoute)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must show backlink to PurchaseSubType when Other + subtype .99 and country has multiple other options" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("10.6", "purchase.sub.other.6"), ("10.99", "purchase.sub.other.99"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val userAnswers = emptyUserAnswers
        .set(pages.RefundingCountryPage, "BE")
        .success
        .value
        .set(pages.PurchaseTypePage, Other)
        .success
        .value
        .set(pages.PurchaseSubTypePage, "10.99")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig)).build()

      running(application) {
        val request = FakeRequest(GET, describeItemsOnInvoiceRoute)
        val view = application.injector.instanceOf[DescribeItemsOnInvoiceView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form,
               NormalMode,
               controllers.purchase.routes.PurchaseSubTypeController.onPageLoad(PurchaseType.urlSlugForPurchaseType(Other), NormalMode)
              )(request, messages(application)).toString
        )
      }
    }

    "redirect to Journey Recovery for a POST if no existing data is found" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, describeItemsOnInvoiceRoute)
            .withFormUrlEncodedBody(("value", "Fuel and transport costs"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
