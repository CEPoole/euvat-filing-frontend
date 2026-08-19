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

package views

import base.SpecBase
import models.NormalMode
import org.jsoup.Jsoup
import play.api.test.Helpers.*
import play.api.test.FakeRequest
import views.html.SupplierTaxIdentifierWarningView

class SupplierTaxIdentifierWarningViewSpec extends SpecBase {

  "SupplierTaxIdentifierWarning view" - {

    "must render change links and form action correctly in NormalMode" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val view = application.injector.instanceOf[SupplierTaxIdentifierWarningView]

        val req = FakeRequest()

        val html = view(
          controllers.routes.SupplierTaxIdentifierNumberController.onPageLoad(NormalMode),
          controllers.routes.InvoiceNumberController.onPageLoad(NormalMode),
          controllers.routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode),
          NormalMode
        )(req, messages(application)).toString

        val doc = Jsoup.parse(html)

        doc.select(s"a[href='${controllers.routes.SupplierTaxIdentifierNumberController.onPageLoad(NormalMode).url}']").size() mustBe 1
        doc.select(s"a[href='${controllers.routes.InvoiceNumberController.onPageLoad(NormalMode).url}']").size() mustBe 1

        // form action should post to the warning submit route
        doc.select(s"form[action='${controllers.routes.SupplierTaxIdentifierWarningController.onSubmit(NormalMode).url}']").size() mustBe 1

        // continue button label
        doc.select("button").text() must include(messages(application)("supplierTaxIdentifierWarning.confirm"))
      }
    }

    "must render change links and form action correctly in CheckMode" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val view = application.injector.instanceOf[SupplierTaxIdentifierWarningView]

        val req = FakeRequest()

        val html = view(
          controllers.routes.SupplierTaxIdentifierNumberController.onPageLoad(models.CheckMode),
          controllers.routes.InvoiceNumberController.onPageLoad(models.CheckMode),
          controllers.routes.TotalPurchaseAmountBeforeVatController.onPageLoad(models.CheckMode),
          models.CheckMode
        )(req, messages(application)).toString

        val doc = Jsoup.parse(html)

        doc.select(s"a[href='${controllers.routes.SupplierTaxIdentifierNumberController.onPageLoad(models.CheckMode).url}']").size() mustBe 1
        doc.select(s"a[href='${controllers.routes.InvoiceNumberController.onPageLoad(models.CheckMode).url}']").size() mustBe 1

        doc.select(s"form[action='${controllers.routes.SupplierTaxIdentifierWarningController.onSubmit(models.CheckMode).url}']").size() mustBe 1
      }
    }
  }
}
