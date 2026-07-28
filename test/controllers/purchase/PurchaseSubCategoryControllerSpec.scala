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

package controllers.purchase

import base.SpecBase
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.inject.bind
import utils.ConfigPurchaseMapping
import controllers.routes
import play.api.mvc.Call

import org.mockito.ArgumentCaptor
import forms.PurchaseSubTypeFormProvider

class PurchaseSubCategoryControllerSpec extends SpecBase with MockitoSugar {

  val formProvider = new PurchaseSubTypeFormProvider()
  val form = formProvider()

  "PurchaseSubCategory Controller" - {

    "must return OK when subcategories exist" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq(("1.1", "purchase.sub.test.1.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(GET, "/file-eu-vat/fuel-type")
        val result = route(application, request).value

        status(result) mustEqual OK
      }
    }

      "must clear stored subcategory and label when CountryChangedPage is true" in {
        val fakeConfig = new ConfigPurchaseMapping() {
          override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq(("1.1", "purchase.sub.test.1.1"))
          override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
        }

        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

        val userAnswers = emptyUserAnswers
          .set(pages.RefundingCountryPage, "DE").success.value
          .set(pages.PurchaseSubCategoryPage, "1.1").success.value
          .set(pages.PurchaseSubCategoryLabelPage, "label").success.value
          .set(pages.CountryChangedPage, true).success.value

        val application = applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[ConfigPurchaseMapping].toInstance(fakeConfig),
            bind[repositories.SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

        running(application) {
          val request = FakeRequest(GET, "/file-eu-vat/fuel-type")
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual "/file-eu-vat/fuel-type"

          val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
          verify(mockSessionRepository, times(1)).set(captor.capture())
          val saved = captor.getValue
          saved.get(pages.PurchaseSubCategoryPage) mustBe None
          saved.get(pages.PurchaseSubCategoryLabelPage) mustBe None
          saved.get(pages.CountryChangedPage) mustBe None
        }
      }

    "must redirect to InvoiceType when no subcategories exist" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq.empty
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(GET, "/file-eu-vat/fuel-type")
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.InvoiceTypeController.onPageLoad(models.NormalMode).url
      }
    }

    "must save selection and redirect to InvoiceType on submit" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq(("1.1", "purchase.sub.fuel.1.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, "/file-eu-vat/fuel-type")
          .withFormUrlEncodedBody(("value", "1.1"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.InvoiceTypeController.onPageLoad(models.NormalMode).url

        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.PurchaseSubCategoryPage) mustBe Some("1.1")
        saved.get(pages.PurchaseSubCategoryLabelPage).isDefined mustBe true
      }
    }

    "must remove subcategory and redirect to InvoiceType when None selected" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq(("1.1", "purchase.sub.fuel.1.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val userAnswers = emptyUserAnswers
        .set(pages.RefundingCountryPage, "DE").success.value
        .set(pages.PurchaseSubCategoryPage, "1.1").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, "/file-eu-vat/fuel-type")
          .withFormUrlEncodedBody(("value", ConfigPurchaseMapping.NoneValue))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.InvoiceTypeController.onPageLoad(models.NormalMode).url

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.PurchaseSubCategoryPage) mustBe Some(ConfigPurchaseMapping.NoneValue)
        saved.get(pages.PurchaseSubCategoryLabelPage) mustBe Some(ConfigPurchaseMapping.NoneValue)
      }
    }

    "must persist parent PurchaseSubTypePage when arriving for the first time" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq(("1.1", "purchase.sub.fuel.1.1"))
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.fuel.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(GET, "/file-eu-vat/fuel-type")
        val result = route(application, request).value

        status(result) mustEqual OK

        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.PurchaseSubTypePage) mustBe Some("1.1")
        saved.get(pages.PurchaseSubTypeLabelPage).isDefined mustBe true
      }
    }

    "must work when RefundingCountryNamePage is 'Austria,AT' and persist child selection" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = if (subcode == "1.1") Seq(("1.1.4", "purchase.sub.fuel.1.1.4")) else Seq.empty
        override def subcodesFor(country: String, parentKey: String) = Seq(("1.1", "purchase.sub.fuel.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryNamePage, "Austria,AT").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val controller = application.injector.instanceOf[controllers.purchase.PurchaseSubCategoryController]

        val getRequest = FakeRequest(GET, "/")
        val getResult = controller.onPageLoad("fuel-use", "1.1", models.NormalMode).apply(getRequest)
        status(getResult) mustEqual OK

        val postRequest = FakeRequest(POST, "/").withFormUrlEncodedBody(("value", "1.1.4"))
        val postResult = controller.onSubmit("fuel-use", "1.1", models.NormalMode).apply(postRequest)
        status(postResult) mustEqual SEE_OTHER
        redirectLocation(postResult).value mustEqual routes.InvoiceTypeController.onPageLoad(models.NormalMode).url

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(2)).set(captor.capture())
        val savedList = captor.getAllValues
        val saved = savedList.get(savedList.size() - 1).asInstanceOf[models.UserAnswers]
        saved.get(pages.PurchaseSubCategoryPage) mustBe Some("1.1.4")
      }
    }

    "must not persist parent when it's already present" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq(("1.1", "purchase.sub.fuel.1.1"))
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.fuel.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val userAnswers = emptyUserAnswers
        .set(pages.RefundingCountryPage, "DE").success.value
        .set(pages.PurchaseSubTypePage, "1.1").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(GET, "/file-eu-vat/fuel-type")
        val result = route(application, request).value

        status(result) mustEqual OK

        // sessionRepository.set should not be called because parent already present
        verify(mockSessionRepository, times(0)).set(any())
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq(("1.1", "purchase.sub.fuel.1.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(POST, "/file-eu-vat/fuel-type")
          .withFormUrlEncodedBody(("value", ""))

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include ("There is a problem")
        contentAsString(result) must include (messages(application)("error.required"))
      }
    }

    "must display inline error message above radio buttons when no radio button is selected" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq(("1.1", "purchase.sub.test.1.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(POST, "/file-eu-vat/fuel-type")
          .withFormUrlEncodedBody(("value", ""))

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include ("There is a problem")
        contentAsString(result) must include (messages(application)("error.required"))
      }
    }

  }
}
