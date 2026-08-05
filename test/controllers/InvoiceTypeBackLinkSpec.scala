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
import models.{NormalMode, PurchaseType}
import pages.{InvoiceTypePage, PurchaseTypePage, PurchaseSubCategoryPage, PurchaseSubTypePage}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.mvc.Call

class InvoiceTypeBackLinkSpec extends SpecBase {

  lazy val invoiceRoute = routes.InvoiceTypeController.onPageLoad(NormalMode).url
  val formProvider = new forms.InvoiceTypeFormProvider()
  val form = formProvider()

  "InvoiceTypeController back link" - {

    "should redirect to JourneyRecovery when no user answers available" in {
      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, invoiceRoute)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "should link to PurchaseType when only PurchaseTypePage present" in {
      val userAnswers = emptyUserAnswers.set(PurchaseTypePage, PurchaseType.Fuel).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceRoute)
        val view = application.injector.instanceOf[views.html.InvoiceTypeView]

        val result = play.api.test.Helpers.route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, controllers.routes.PurchaseTypeController.onPageLoad(NormalMode))(request, messages(application)).toString)
      }
    }

    "should fallback to PurchaseType when child present but parent missing" in {
      val userAnswers = emptyUserAnswers.set(PurchaseTypePage, PurchaseType.Fuel).success.value
      .set(PurchaseSubCategoryPage, "1").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceRoute)
        val view = application.injector.instanceOf[views.html.InvoiceTypeView]

        val result = play.api.test.Helpers.route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, controllers.routes.PurchaseTypeController.onPageLoad(NormalMode))(request, messages(application)).toString)
      }
    }

    "should link to PurchaseSubCategory when parent and child present" in {
      val userAnswers = emptyUserAnswers
      .set(PurchaseTypePage, PurchaseType.Fuel).success.value
      .set(PurchaseSubTypePage, "1").success.value
      .set(PurchaseSubCategoryPage, "1").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, invoiceRoute)
        val view = application.injector.instanceOf[views.html.InvoiceTypeView]
        val formProvider = new forms.InvoiceTypeFormProvider()
        val form = formProvider()

        val result = route(application, request).value

        status(result) mustEqual OK
        normalizeHtml(contentAsString(result)) mustEqual normalizeHtml(view(form, NormalMode, Call("GET", "/file-eu-vat/fuel-type"))(request, messages(application)).toString)
      }
    }

  }
}
