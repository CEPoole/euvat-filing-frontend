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
import forms.PurchaseTypeFormProvider
import models.responses.{AddPurchaseResponse, ApplicationResponse}
import models.{CheckMode, NormalMode, PurchaseType}
import navigation.{FakeNavigator, Navigator}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, times, verify, when}
import org.mockito.ArgumentCaptor
import org.scalatestplus.mockito.MockitoSugar
import pages.PurchaseTypePage
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import queries.ClaimApplicationResponseQuery
import repositories.SessionRepository
import utils.ConfigPurchaseMapping
import services.EuVatRefundsService
import views.html.PurchaseTypeView

import scala.concurrent.Future

class PurchaseTypeControllerSpec extends SpecBase with MockitoSugar {

  val onwardRoute: Call = Call("GET", "/foo")

  lazy val purchaseTypeRoute: String = routes.PurchaseTypeController.onPageLoad(NormalMode).url
  lazy val purchaseTypeSubmitRoute: String = routes.PurchaseTypeController.onSubmit(NormalMode).url
  lazy val backLinkCall: Call = routes.BeforeYouStartPurchaseController.onPageLoad()

  lazy val purchaseTypeRouteCheck: String = routes.PurchaseTypeController.onPageLoad(CheckMode).url
  lazy val purchaseTypeSubmitRouteCheck: String = routes.PurchaseTypeController.onSubmit(CheckMode).url
  lazy val backLinkCallCheck: Call = routes.BeforeYouStartPurchaseController.onPageLoad()

  "PurchaseType Controller" - {

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLinkCall)(request, messages(application)).toString)
      }
    }

    "must short-circuit to CYA when arrived-from-describe but no description and only 'none' subcode exists" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1.99", "purchase.sub.other.1.99"))
      }

      val userAnswers = emptyUserAnswers
        .set(pages.PurchaseTypePage, PurchaseType.Other)
        .success
        .value
        .set(pages.DescribeItemsArrivedFromCheckYourAnswersPage, true)
        .success
        .value
        .set(pages.RefundingCountryPage, "EE")
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.PurchaseTypeController.onSubmit(CheckMode).url)
          .withFormUrlEncodedBody("value" -> PurchaseType.Other.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
        verify(mockSessionRepository, times(0)).set(any())
      }
    }

    "must return OK and the correct view for a GET when simplified invoice check exists with back link to simplified check" in {

      val userAnswers = emptyUserAnswers.set(pages.SimplifiedInvoiceVatRegCheckPage, false).success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, NormalMode, routes.BeforeYouStartPurchaseController.onPageLoad())(
            request,
            messages(application)
          ).toString
        )
      }
    }

    "must return OK and the correct view for a GET when country is Germany with back link to SupplierTaxIdentifierNumber" in {

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, NormalMode, backLinkCall)(request, messages(application)).toString
      }
    }

    "must return OK and the correct view for a GET when simplified invoice check exists with value Yes and back link to TotalVatPaid" in {

      val userAnswers = emptyUserAnswers.set(pages.SimplifiedInvoiceVatRegCheckPage, true).success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLinkCall)(request, messages(application)).toString)
      }
    }

    "must return OK and the correct view for a GET in CheckMode with correct back link" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRouteCheck)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(
          view(form, CheckMode, backLinkCallCheck)(request, messages(application)).toString
        )
      }
    }

    "must return OK and the correct view for a GET in CheckMode when country is Germany with back link to SupplierTaxIdentifierNumber" in {

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRouteCheck)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider()

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(form, CheckMode, backLinkCallCheck)(request, messages(application)).toString
      }
    }

    "must redirect to Journey Recovery when no existing data is found on GET" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must populate the view with the saved value on GET" in {

      val userAnswers = emptyUserAnswers.set(PurchaseTypePage, PurchaseType.Fuel).success.value
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseTypeView]
        val formProvider = application.injector.instanceOf[PurchaseTypeFormProvider]
        val form = formProvider().fill(PurchaseType.Fuel)

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, backLinkCall)(request, messages(application)).toString)
      }
    }

    "must redirect to the next page and persist the answer when valid data is submitted" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))
      when(mockEuVatRefundsService.addPurchase(any())(any()))
        .thenReturn(Future.successful(AddPurchaseResponse(1, 2)))

      val userAnswers = emptyUserAnswers
        .set(ClaimApplicationResponseQuery, ApplicationResponse(134, "GB123134", 1))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRoute)
          .withFormUrlEncodedBody("value" -> PurchaseType.Fuel.toString)
        val result = route(application, request).value
        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual ("/file-eu-vat" + onwardRoute.url)
        verify(mockSessionRepository, times(2)).set(any())
      }
    }

    "must redirect to InvoiceType when no subcodes exist for the selected purchase type" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))
      when(mockEuVatRefundsService.addPurchase(any())(any()))
        .thenReturn(Future.successful(AddPurchaseResponse(1, 2)))

      // set country to LT which has no 'fuel' mapping in purchase-mapping.conf
      val userAnswers = emptyUserAnswers
        .set(pages.RefundingCountryPage, "LT")
        .success
        .value
        .set(queries.ClaimApplicationResponseQuery, ApplicationResponse(134, "GB123134", 1))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRoute)
          .withFormUrlEncodedBody("value" -> PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual "/file-eu-vat/invoice-type"
        verify(mockSessionRepository, times(2)).set(any())
      }
    }

    "must redirect to change sub-type page in CheckMode when subcodes exist for the selected purchase type" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val fakeConfig = new utils.ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.fuel.1"))
      }

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRouteCheck)
          .withFormUrlEncodedBody("value" -> models.PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.PurchaseSubTypeController
          .onPageLoad(models.PurchaseType.slugOf(models.PurchaseType.Fuel), models.CheckMode)
          .url
      }
    }

    "must clear DescribeItemsOnInvoice when purchase type is changed on POST" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val userAnswers = emptyUserAnswers
        .set(pages.PurchaseTypePage, PurchaseType.Fuel)
        .success
        .value
        .set(pages.DescribeItemsOnInvoicePage, "details")
        .success
        .value
        .set(ClaimApplicationResponseQuery, ApplicationResponse(134, "GB123134", 1))
        .success
        .value

      when(mockEuVatRefundsService.addPurchase(any())(any())).thenReturn(Future.successful(AddPurchaseResponse(1, 2)))

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRoute)
          .withFormUrlEncodedBody("value" -> PurchaseType.Transport.toString)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual ("/file-eu-vat" + onwardRoute.url)
        verify(mockEuVatRefundsService, times(1)).addPurchase(any())(any())
      }
    }

    "must persist AddPurchaseResponse in session when addPurchase succeeds" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))
      val addResp = AddPurchaseResponse(itemNumber = 42, updateSequenceNumber = 3)
      when(mockEuVatRefundsService.addPurchase(any())(any()))
        .thenReturn(Future.successful(addResp))

      val userAnswers = emptyUserAnswers
        .set(ClaimApplicationResponseQuery, ApplicationResponse(134, "GB123134", 1))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRoute)
          .withFormUrlEncodedBody("value" -> PurchaseType.Transport.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        val captor: ArgumentCaptor[models.UserAnswers] = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(2)).set(captor.capture())
        val savedAnswers = captor.getAllValues.get(1)

        savedAnswers.get(pages.AddPurchaseResponsePage).value mustEqual addResp
      }
    }

    "must short-circuit to purchase CYA in CheckMode when value unchanged" in {
      val userAnswers = emptyUserAnswers.set(pages.PurchaseTypePage, PurchaseType.Fuel).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.PurchaseTypeController.onSubmit(CheckMode).url)
          .withFormUrlEncodedBody("value" -> PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
      }
    }

    "must persist and continue in CheckMode when value changed" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.PurchaseTypeController.onSubmit(CheckMode).url)
          .withFormUrlEncodedBody("value" -> PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        // In CheckMode with subcodes present we now redirect to the change-<slug> path
        redirectLocation(result).value mustEqual controllers.purchase.routes.PurchaseSubTypeController
          .onPageLoad(models.PurchaseType.slugOf(models.PurchaseType.Fuel), models.CheckMode)
          .url
        verify(mockSessionRepository, times(1)).set(any())
      }
    }

    "must redirect to change-describe-items when arrived-from-describe flag set" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val fakeConfig = new utils.ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.fuel.1"))
      }

      val userAnswers = emptyUserAnswers.set(pages.DescribeItemsArrivedFromCheckYourAnswersPage, true).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRouteCheck)
          .withFormUrlEncodedBody("value" -> models.PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(CheckMode).url
        verify(mockSessionRepository, times(2)).set(any())
      }
    }

    "must redirect to change-describe-items when arrived-from-describe flag set and value unchanged" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val userAnswers = emptyUserAnswers
        .set(pages.PurchaseTypePage, PurchaseType.Fuel)
        .success
        .value
        .set(pages.DescribeItemsArrivedFromCheckYourAnswersPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.PurchaseTypeController.onSubmit(CheckMode).url)
          .withFormUrlEncodedBody("value" -> PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(CheckMode).url
        verify(mockSessionRepository, times(1)).set(any())
      }
    }

    "must short-circuit to CYA when arrived-from-describe and also arrived-from-subtype present and value unchanged" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val userAnswers = emptyUserAnswers
        .set(pages.PurchaseTypePage, PurchaseType.Fuel)
        .success
        .value
        .set(pages.DescribeItemsArrivedFromCheckYourAnswersPage, true)
        .success
        .value
        .set(pages.PurchaseSubTypeArrivedFromCheckYourAnswersPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.PurchaseTypeController.onSubmit(CheckMode).url)
          .withFormUrlEncodedBody("value" -> PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        // unchanged submission should short-circuit to CYA despite describe-items arrival when subtype-arrival also present
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
        verify(mockSessionRepository, times(0)).set(any())
      }
    }

    "must persist and redirect to CYA in CheckMode when value changed and no subcodes exist" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      // set country to LT which has no 'fuel' mapping in purchase-mapping.conf
      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "LT").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.PurchaseTypeController.onSubmit(CheckMode).url)
          .withFormUrlEncodedBody("value" -> PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
        verify(mockSessionRepository, times(1)).set(any())
      }
    }

    "must redirect to change-sub-type when arrived-from-subcategory flag set" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val fakeConfig = new utils.ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.fuel.1"))
      }

      val userAnswers = emptyUserAnswers.set(pages.PurchaseSubCategoryArrivedFromCheckYourAnswersPage, true).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRouteCheck)
          .withFormUrlEncodedBody("value" -> models.PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.PurchaseSubTypeController
          .onPageLoad(models.PurchaseType.slugOf(models.PurchaseType.Fuel), models.CheckMode)
          .url
        verify(mockSessionRepository, times(2)).set(any())
      }
    }

    "must redirect to change-sub-type when arrived-from-subcategory flag set and value unchanged" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val userAnswers = emptyUserAnswers
        .set(pages.PurchaseTypePage, PurchaseType.Fuel)
        .success
        .value
        .set(pages.PurchaseSubCategoryArrivedFromCheckYourAnswersPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.PurchaseTypeController.onSubmit(CheckMode).url)
          .withFormUrlEncodedBody("value" -> PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        // unchanged submission should short-circuit to CYA even when arrival flag present
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
        verify(mockSessionRepository, times(0)).set(any())
      }
    }

    "must redirect to change-sub-type when arrived-from-subtype flag set" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val fakeConfig = new utils.ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.fuel.1"))
      }

      val userAnswers = emptyUserAnswers.set(pages.PurchaseSubTypeArrivedFromCheckYourAnswersPage, true).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRouteCheck)
          .withFormUrlEncodedBody("value" -> models.PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.PurchaseSubTypeController
          .onPageLoad(models.PurchaseType.slugOf(models.PurchaseType.Fuel), models.CheckMode)
          .url
        verify(mockSessionRepository, times(2)).set(any())
      }
    }

    "must redirect to change-sub-type when arrived-from-subtype flag set and value unchanged" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val userAnswers = emptyUserAnswers
        .set(pages.PurchaseTypePage, PurchaseType.Fuel)
        .success
        .value
        .set(pages.PurchaseSubTypeArrivedFromCheckYourAnswersPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, routes.PurchaseTypeController.onSubmit(CheckMode).url)
          .withFormUrlEncodedBody("value" -> PurchaseType.Fuel.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        // unchanged submission should short-circuit to CYA even when arrival flag present
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
        verify(mockSessionRepository, times(0)).set(any())
      }
    }

    "must clear the purchase chain when CountryChangedPage is true" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(scala.concurrent.Future.successful(true))

      val userAnswers = emptyUserAnswers
        .set(pages.PurchaseTypePage, PurchaseType.Fuel)
        .success
        .value
        .set(pages.PurchaseSubTypePage, "1")
        .success
        .value
        .set(pages.PurchaseSubTypeLabelPage, "lbl")
        .success
        .value
        .set(pages.PurchaseSubCategoryPage, "1.1")
        .success
        .value
        .set(pages.PurchaseSubCategoryLabelPage, "lbl2")
        .success
        .value
        .set(pages.CountryChangedPage, true)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val request = FakeRequest(GET, purchaseTypeRoute)
        val result = route(application, request).value

        status(result) mustEqual OK

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.PurchaseTypePage) mustBe None
        saved.get(pages.PurchaseSubTypePage) mustBe None
        saved.get(pages.PurchaseSubTypeLabelPage) mustBe None
        saved.get(pages.PurchaseSubCategoryPage) mustBe None
        saved.get(pages.PurchaseSubCategoryLabelPage) mustBe None
        saved.get(pages.CountryChangedPage) mustBe None
      }
    }

    "must add the purchase, persist the response, and redirect when valid data is submitted" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))
      when(mockEuVatRefundsService.addPurchase(any())(any()))
        .thenReturn(Future.successful(AddPurchaseResponse(itemNumber = 1, updateSequenceNumber = 2)))

      val userAnswers = emptyUserAnswers
        .set(ClaimApplicationResponseQuery, ApplicationResponse(134, "GB123134", 1))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRoute)
          .withFormUrlEncodedBody("value" -> PurchaseType.Transport.toString)
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual ("/file-eu-vat" + onwardRoute.url)
        verify(mockEuVatRefundsService, times(1)).addPurchase(any())(any())
      }
    }

    "must redirect to Journey Recovery when the addPurchase call fails" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))
      when(mockEuVatRefundsService.addPurchase(any())(any()))
        .thenReturn(Future.failed(new RuntimeException("boom")))

      val userAnswers = emptyUserAnswers
        .set(ClaimApplicationResponseQuery, ApplicationResponse(134, "GB123134", 1))
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRoute)
          .withFormUrlEncodedBody("value" -> PurchaseType.Transport.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.DescribeItemsOnInvoicePage) mustBe None
      }
    }

    "must redirect to the next page when applicationId is missing on submit" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())).thenReturn(Future.successful(true))

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers))
        .overrides(
          bind[Navigator].toInstance(new FakeNavigator(onwardRoute)),
          bind[SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, purchaseTypeSubmitRoute)
          .withFormUrlEncodedBody("value" -> PurchaseType.Transport.toString)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual ("/file-eu-vat" + onwardRoute.url)
        verify(mockEuVatRefundsService, never).addPurchase(any())(any())
      }
    }
  }
}
