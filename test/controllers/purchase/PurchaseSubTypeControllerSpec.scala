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
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.inject.bind
import utils.ConfigPurchaseMapping
import controllers.routes
import play.api.mvc.Call
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import uk.gov.hmrc.govukfrontend.views.Aliases

import org.mockito.ArgumentCaptor
import forms.PurchaseSubTypeFormProvider

class PurchaseSubTypeControllerSpec extends SpecBase with MockitoSugar {

  val onwardRoute: Call = Call("GET", "/foo")

  val formProvider = new PurchaseSubTypeFormProvider()
  val form = formProvider()

  "PurchaseSubType Controller" - {

    "must return OK and the correct view when options exist" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.test.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(GET, "/file-eu-vat/fuel-use")
        val result = route(application, request).value

        status(result) mustEqual OK
      }
    }

    "must redirect to JourneyRecovery when country is missing" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.test.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val application = applicationBuilder(userAnswers = None)
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(GET, "/file-eu-vat/fuel-use")
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to InvoiceType when no options exist" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq.empty
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(GET, "/file-eu-vat/fuel-use")
        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.InvoiceTypeController.onPageLoad(models.NormalMode).url
      }
    }

    "must save selection and redirect to PurchaseSubCategory when children exist" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.fuel.1"), ("1.1", "purchase.sub.fuel.1.1"))
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
        val request = FakeRequest(POST, "/file-eu-vat/fuel-use")
          .withFormUrlEncodedBody(("value", "1"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual "/file-eu-vat/fuel-type"

        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.PurchaseSubTypePage) mustBe Some("1")
      }
    }

    "must handle RefundingCountryNamePage stored as 'Austria,AT' and show children" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1.1", "purchase.sub.fuel.1"), ("1.3", "purchase.sub.fuel.3"))
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = if (subcode == "1.1") Seq(("1.1.1", "purchase.sub.fuel.1.1"), ("1.1.2", "purchase.sub.fuel.1.2")) else Seq.empty
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
        val request = FakeRequest(POST, "/file-eu-vat/fuel-use")
          .withFormUrlEncodedBody(("value", "1.1"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        val loc = redirectLocation(result).value
        // The controller should route to the friendly subcategory path defined
        // by PurchaseSubCategoryType for dotted parent codes (e.g. "1.1").
        loc must include ("/file-eu-vat/fuel-type")
        loc mustNot include ("parentCode=")

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.PurchaseSubTypePage) mustBe Some("1.1")
      }
    }

    "must clear PurchaseSubCategory and its label when PurchaseSubType is changed on POST" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.fuel.1"), ("2", "purchase.sub.fuel.2"))
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq.empty
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val userAnswers = emptyUserAnswers
        .set(pages.RefundingCountryPage, "DE").success.value
        .set(pages.PurchaseSubTypePage, "1").success.value
        .set(pages.PurchaseSubCategoryPage, "1.1").success.value
        .set(pages.PurchaseSubCategoryLabelPage, "lbl").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, "/file-eu-vat/fuel-use")
          .withFormUrlEncodedBody(("value", "2"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue

        saved.get(pages.PurchaseSubCategoryPage) mustBe None
        saved.get(pages.PurchaseSubCategoryLabelPage) mustBe None
        saved.get(pages.PurchaseSubTypePage) mustBe Some("2")
      }
    }

    "must save selection and redirect to InvoiceType when no children exist" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.fuel.1"))
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq.empty
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
        val request = FakeRequest(POST, "/file-eu-vat/fuel-use")
          .withFormUrlEncodedBody(("value", "1"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.InvoiceTypeController.onPageLoad(models.NormalMode).url

        val captor = ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.PurchaseSubTypePage) mustBe Some("1")
        saved.get(pages.PurchaseSubTypeLabelPage).isDefined mustBe true
      }
    }

    "must return a Bad Request and errors when invalid data is submitted" in {

      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.test.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(POST, "/file-eu-vat/fuel-use")
          .withFormUrlEncodedBody(("value", ""))

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include ("There is a problem")
        // inline error summary should show the required message
        contentAsString(result) must include (messages(application)("purchase.sub.fuel.error.required"))
      }
    }

    "must return OK when PurchaseTypePage present and slug unknown" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.test.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val userAnswers = emptyUserAnswers
        .set(pages.RefundingCountryPage, "DE").success.value
        .set(pages.PurchaseTypePage, models.PurchaseType.Fuel).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(GET, "/file-eu-vat/fuel-use")
        val result = route(application, request).value

        status(result) mustEqual OK
      }
    }

    "must not show None option when PurchaseType is Other" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.test.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq(
          RadioItem(
            content = Aliases.Text(msgs("purchase.sub.test.1")),
            value = Some("1"),
            id = Some("value_0")
          ),
          RadioItem(
            content = Aliases.Text(ConfigPurchaseMapping.NoneValue),
            value = Some(ConfigPurchaseMapping.NoneValue),
            id = Some("value_none")
          )
        )
      }

      val userAnswers = emptyUserAnswers
        .set(pages.RefundingCountryPage, "AT").success.value
        .set(pages.PurchaseTypePage, models.PurchaseType.Other).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(GET, "/file-eu-vat/purchase-type-other")
        val result = route(application, request).value

        status(result) mustEqual OK
        val body = contentAsString(result)
        body mustNot include (ConfigPurchaseMapping.NoneValue)
      }
    }

      "must clear stored subtype and label when CountryChangedPage is true" in {
        val fakeConfig = new ConfigPurchaseMapping() {
          override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.test.1"))
          override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
        }

        val mockSessionRepository = mock[repositories.SessionRepository]
        when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

        val userAnswers = emptyUserAnswers
          .set(pages.RefundingCountryPage, "DE").success.value
          .set(pages.PurchaseSubTypePage, "1").success.value
          .set(pages.PurchaseSubTypeLabelPage, "label").success.value
          .set(pages.CountryChangedPage, true).success.value

        val application = applicationBuilder(userAnswers = Some(userAnswers))
          .overrides(
            bind[ConfigPurchaseMapping].toInstance(fakeConfig),
            bind[repositories.SessionRepository].toInstance(mockSessionRepository)
          )
          .build()

        running(application) {
          val request = FakeRequest(GET, "/file-eu-vat/fuel-use")
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual "/file-eu-vat/fuel-use"

          val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
          verify(mockSessionRepository, times(1)).set(captor.capture())
          val saved = captor.getValue
          saved.get(pages.PurchaseSubTypePage) mustBe None
          saved.get(pages.PurchaseSubTypeLabelPage) mustBe None
          saved.get(pages.CountryChangedPage) mustBe None
        }
      }

    "must save and redirect when parent comes from user answers (children exist)" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.fuel.1"), ("1.1", "purchase.sub.fuel.1.1"))
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq(("1.1", "purchase.sub.fuel.1.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val userAnswers = emptyUserAnswers
        .set(pages.RefundingCountryPage, "DE").success.value
        .set(pages.PurchaseTypePage, models.PurchaseType.Fuel).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, "/file-eu-vat/fuel-use")
          .withFormUrlEncodedBody(("value", "1"))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual "/file-eu-vat/fuel-type"
      }
    }

    "must remove subtype and redirect to InvoiceType when None selected" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.fuel.1"))
        override def subcategoriesFor(country: String, parentKey: String, subcode: String) = Seq.empty
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val mockSessionRepository = mock[repositories.SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn scala.concurrent.Future.successful(true)

      val userAnswers = emptyUserAnswers
        .set(pages.RefundingCountryPage, "DE").success.value
        .set(pages.PurchaseSubTypePage, "1").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(
          bind[ConfigPurchaseMapping].toInstance(fakeConfig),
          bind[repositories.SessionRepository].toInstance(mockSessionRepository)
        )
        .build()

      running(application) {
        val request = FakeRequest(POST, "/file-eu-vat/fuel-use")
          .withFormUrlEncodedBody(("value", ConfigPurchaseMapping.NoneValue))

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.InvoiceTypeController.onPageLoad(models.NormalMode).url

        val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
        verify(mockSessionRepository, times(1)).set(captor.capture())
        val saved = captor.getValue
        saved.get(pages.PurchaseSubTypePage) mustBe Some(ConfigPurchaseMapping.NoneValue)
        saved.get(pages.PurchaseSubTypeLabelPage) mustBe Some(ConfigPurchaseMapping.NoneValue)
        saved.get(pages.PurchaseSubCategoryPage) mustBe None
      }
    }

    "must display inline error message above radio buttons when no radio button is selected" in {
      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("1", "purchase.sub.test.1"))
        override def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages) = Seq.empty
      }

      val userAnswers = emptyUserAnswers.set(pages.RefundingCountryPage, "DE").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[ConfigPurchaseMapping].toInstance(fakeConfig))
        .build()

      running(application) {
        val request = FakeRequest(POST, "/file-eu-vat/fuel-use")
          .withFormUrlEncodedBody(("value", ""))

        val result = route(application, request).value

        status(result) mustEqual BAD_REQUEST
        contentAsString(result) must include ("There is a problem")
        contentAsString(result) must include (messages(application)("purchase.sub.fuel.error.required"))
      }
    }

  }
}
