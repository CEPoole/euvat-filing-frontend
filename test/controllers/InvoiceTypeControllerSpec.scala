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
import forms.InvoiceTypeFormProvider
import models.{CheckMode, InvoiceType, NormalMode, UserAnswers}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatestplus.mockito.MockitoSugar
import pages.{DescribeItemsOnInvoicePage, InvoiceTypePage, PurchaseSubCategoryPage, PurchaseSubTypePage, PurchaseTypePage, SimplifiedInvoiceVatRegCheckPage, SupplierTaxNumberPage}
import models.PurchaseType
import play.api.inject.bind
import utils.ConfigPurchaseMapping
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import views.html.InvoiceTypeView

import scala.concurrent.Future

class InvoiceTypeControllerSpec extends SpecBase with MockitoSugar {

  def onwardRoute = Call("GET", "/foo")

  lazy val invoiceTypeRoute = routes.InvoiceTypeController.onPageLoad(NormalMode).url

  val formProvider = new InvoiceTypeFormProvider()
  val form = formProvider()

  "InvoiceType Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceTypeRoute)

        val result = route(application, request).value

        val view = application.injector.instanceOf[InvoiceTypeView]

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, routes.PurchaseTypeController.onPageLoad(NormalMode))(request, messages(application)).toString
        )
      }
    }

    "must show backlink to PurchaseSubType when PurchaseSubTypePage present" in {

      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, PurchaseType.Fuel)
        .success
        .value
        .set(PurchaseSubTypePage, "1")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceTypeRoute)

        val view = application.injector.instanceOf[InvoiceTypeView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, Call("GET", "/file-eu-vat/fuel-use"))(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must show backlink to PurchaseSubCategory when PurchaseSubCategoryPage present" in {

      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, PurchaseType.Fuel)
        .success
        .value
        .set(PurchaseSubTypePage, "1")
        .success
        .value
        .set(PurchaseSubCategoryPage, "1")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()
      running(application) {
        val request = FakeRequest(GET, invoiceTypeRoute)

        val view = application.injector.instanceOf[InvoiceTypeView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, Call("GET", "/file-eu-vat/fuel-type"))(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must show backlink to DescribeItemsOnInvoice when PurchaseType is Other and subcategory ends with 99" in {

      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, PurchaseType.Other)
        .success
        .value
        .set(PurchaseSubCategoryPage, "1.99")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceTypeRoute)

        val view = application.injector.instanceOf[InvoiceTypeView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, routes.DescribeItemsOnInvoiceController.onPageLoad(NormalMode))(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must show backlink to DescribeItemsOnInvoice when PurchaseType is Other and parent sub-type ends with 99" in {

      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, PurchaseType.Other)
        .success
        .value
        .set(PurchaseSubTypePage, "1.99")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceTypeRoute)

        val view = application.injector.instanceOf[InvoiceTypeView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, routes.DescribeItemsOnInvoiceController.onPageLoad(NormalMode))(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must show backlink to PurchaseSubCategory when PurchaseSubCategoryPage present but PurchaseSubTypePage missing" in {

      val child = "1.2"
      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, PurchaseType.Transport)
        .success
        .value
        .set(PurchaseSubCategoryPage, child)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceTypeRoute)

        val view = application.injector.instanceOf[InvoiceTypeView]

        val result = route(application, request).value

        status(result) mustEqual OK
        val expectedParent = child.split("\\.").headOption.getOrElse(child)
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, Call("GET", "/file-eu-vat/what-transport-cost"))(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must NOT show backlink to DescribeItemsOnInvoice when DescribeItemsOnInvoicePage present but PurchaseType is not Other" in {

      val userAnswers = emptyUserAnswers
        .set(DescribeItemsOnInvoicePage, "details")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceTypeRoute)

        val view = application.injector.instanceOf[InvoiceTypeView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, routes.PurchaseTypeController.onPageLoad(NormalMode))(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must render OK when PurchaseSubTypePage contains dotted parent code" in {
      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, PurchaseType.Fuel)
        .success
        .value
        .set(PurchaseSubTypePage, "1.10")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceTypeRoute)
        val result = route(application, request).value

        status(result) mustEqual OK
      }
    }

    "must populate the view correctly on a GET when the question has previously been answered" in {

      val userAnswers = UserAnswers(userAnswersId).set(InvoiceTypePage, InvoiceType.values.head).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceTypeRoute)

        val view = application.injector.instanceOf[InvoiceTypeView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form.fill(InvoiceType.values.head), NormalMode, routes.PurchaseTypeController.onPageLoad(NormalMode))(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must show backlink to DescribeItemsOnInvoice when PurchaseType is Other, parent ends with 99, and country has multiple other options" in {

      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) =
          if (country == "BE" && parentKey == "other") Seq(("10.6", "purchase.sub.other.6"), ("10.99", "purchase.sub.other.99"))
          else super.subcodesFor(country, parentKey)
      }

      val userAnswers = emptyUserAnswers
        .set(PurchaseTypePage, PurchaseType.Other)
        .success
        .value
        .set(PurchaseSubTypePage, "10.99")
        .success
        .value
        .set(pages.RefundingCountryPage, "BE")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(GET, invoiceTypeRoute)

        val view = application.injector.instanceOf[InvoiceTypeView]

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, routes.DescribeItemsOnInvoiceController.onPageLoad(NormalMode))(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must redirect to the next page when standard invoice is submitted" in {

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
          FakeRequest(POST, invoiceTypeRoute)
            .withFormUrlEncodedBody(("value", InvoiceType.StandardInvoice.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.InvoiceNumberController.onPageLoad(NormalMode).url
      }
    }

    "must clear supplier tax and simplified flag when invoice type is changed" in {

      val mockSessionRepository = mock[SessionRepository]

      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val initialAnswers = emptyUserAnswers
        .set(InvoiceTypePage, InvoiceType.StandardInvoice)
        .success
        .value
        .set(pages.SupplierTaxNumberPage, models.SupplierTaxNumber.Vatregistrationnumber)
        .success
        .value
        .set(SimplifiedInvoiceVatRegCheckPage, true)
        .success
        .value

      val application =
        applicationBuilder(userAnswers = Some(initialAnswers))
          .overrides(
            bind[SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

      running(application) {
        val request =
          FakeRequest(POST, invoiceTypeRoute)
            .withFormUrlEncodedBody(("value", InvoiceType.SimplifiedInvoice.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository).set(captor.capture())
        val saved = captor.getValue

        saved.get(pages.SupplierTaxNumberPage) mustBe None
        saved.get(SimplifiedInvoiceVatRegCheckPage) mustBe None
        saved.get(InvoiceTypePage) mustBe Some(InvoiceType.SimplifiedInvoice)
      }
    }

    "must redirect to the next page when simplified invoice is submitted" in {

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
          FakeRequest(POST, invoiceTypeRoute)
            .withFormUrlEncodedBody(("value", InvoiceType.SimplifiedInvoice.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.InvoiceNumberController.onPageLoad(NormalMode).url
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, invoiceTypeRoute)
            .withFormUrlEncodedBody(("value", "invalid value"))

        val boundForm = form.bind(Map("value" -> "invalid value"))

        val view = application.injector.instanceOf[InvoiceTypeView]

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(boundForm, NormalMode, routes.PurchaseTypeController.onPageLoad(NormalMode))(request, messages(application)).toString
        )
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, invoiceTypeRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request =
          FakeRequest(POST, invoiceTypeRoute)
            .withFormUrlEncodedBody(("value", InvoiceType.values.head.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }

    }

    "must redirect to Check Your Purchase Details when in CheckMode and value unchanged" in {

      val userAnswers = emptyUserAnswers.set(InvoiceTypePage, InvoiceType.StandardInvoice).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request =
          FakeRequest(POST, routes.InvoiceTypeController.onSubmit(CheckMode).url)
            .withFormUrlEncodedBody(("value", InvoiceType.StandardInvoice.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
      }
    }

    "must redirect to SimplifiedInvoiceVatRegCheck in CheckMode when submitted value changes" in {

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
          FakeRequest(POST, routes.InvoiceTypeController.onSubmit(CheckMode).url)
            .withFormUrlEncodedBody(("value", InvoiceType.SimplifiedInvoice.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(CheckMode).url
      }
    }

    "must redirect to SupplierVatRegistrationNumber in CheckMode when submitted value changes to standard" in {

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
          FakeRequest(POST, routes.InvoiceTypeController.onSubmit(CheckMode).url)
            .withFormUrlEncodedBody(("value", InvoiceType.StandardInvoice.toString))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.SupplierVatRegistrationNumberController.onPageLoad(CheckMode).url
      }
    }
  }
}
