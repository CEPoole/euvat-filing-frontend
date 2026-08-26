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

package viewmodels.checkAnswers

import base.SpecBase
import models.{InvoiceType, PurchaseType, SupplierTaxNumber}
import pages.*
import utils.ConfigPurchaseMapping

import java.time.LocalDate

import play.api.test.Helpers.*
import play.api.i18n.Messages
import controllers.routes
import models.CheckMode
import models.UserAnswers
import viewmodels.checkAnswers.CheckYourPurchaseDetailsSummary
import utils.MountPrefix

class CheckYourPurchaseDetailsSummarySpec extends SpecBase {

  "CheckYourPurchaseDetailsSummary" - {

    "rowPurchaseSubCategoryLabel should build change link with resolved slug and mount prefix" in {
      val userAnswers = UserAnswers(userAnswersId)
        .set(RefundingCountryPage, "BG")
        .success
        .value
        .set(PurchaseTypePage, PurchaseType.FoodAndDrink)
        .success
        .value
        .set(PurchaseSubCategoryPage, "7.1")
        .success
        .value
        .set(PurchaseSubCategoryLabelPage, "The taxable person or an employee")
        .success
        .value

      val app = applicationBuilder(userAnswers = Some(userAnswers)).build()
      running(app) {
        implicit val msgs = messages(app)
        implicit val req = play.api.test.FakeRequest()

        // simulate MountPrefix.get via a fake request in SpecBase helpers
        val row = CheckYourPurchaseDetailsSummary.rowPurchaseSubCategoryLabel(userAnswers).value
        val slug = "who-food-drink-for"
        val expected = s"/change-$slug"
        row._3.head._1 must endWith(expected)
      }
    }

    "rowPurchaseSubTypeLabel should display Not provided when NoneValue selected" in {
      val userAnswers = UserAnswers(userAnswersId)
        .set(RefundingCountryPage, "BG")
        .success
        .value
        .set(PurchaseTypePage, PurchaseType.FoodAndDrink)
        .success
        .value
        .set(PurchaseSubTypePage, ConfigPurchaseMapping.NoneValue)
        .success
        .value
        .set(PurchaseSubTypeLabelPage, ConfigPurchaseMapping.NoneValue)
        .success
        .value

      val fakeConfig = new ConfigPurchaseMapping() {
        override def subcodesFor(country: String, parentKey: String) = Seq(("7.1", "purchase.sub.foodAndDrink.7.1"))
      }

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()
      running(application) {
        implicit val msgs = messages(application)

        val rowOpt = CheckYourPurchaseDetailsSummary.rowPurchaseSubTypeLabel(userAnswers, fakeConfig)
        rowOpt.value._2.value mustBe msgs("site.notProvided")
      }
    }

    "rowPurchaseSubCategoryLabel should show Not provided and resolve slug from sub-type when sub-category is None" in {
      val userAnswers = UserAnswers(userAnswersId)
        .set(RefundingCountryPage, "BG")
        .success
        .value
        .set(PurchaseTypePage, PurchaseType.FoodAndDrink)
        .success
        .value
        .set(PurchaseSubTypePage, "7.1")
        .success
        .value
        .set(PurchaseSubCategoryPage, ConfigPurchaseMapping.NoneValue)
        .success
        .value
        .set(PurchaseSubCategoryLabelPage, ConfigPurchaseMapping.NoneValue)
        .success
        .value

      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()
      running(application) {
        implicit val msgs = messages(application)
        implicit val req = play.api.test.FakeRequest()

        val row = CheckYourPurchaseDetailsSummary.rowPurchaseSubCategoryLabel(userAnswers).value
        row._2.value mustBe msgs("site.notProvided")
        row._3.head._1 must endWith("/change-who-food-drink-for")
      }
    }

    "sections should include currency and amount rows when currency provided" in {
      val ua = emptyUserAnswers
        .set(RefundingCountryPage, "AT")
        .success
        .value
        .set(RefundingCurrencyPage, "EUR")
        .success
        .value
        .set(TotalPurchaseAmountBeforeVatPage, BigDecimal(123.45))
        .success
        .value
        .set(TotalVatPaidPage, BigDecimal(10))
        .success
        .value
        .set(TotalVatClaimPage, BigDecimal(5))
        .success
        .value
        .set(PurchaseTypePage, PurchaseType.Fuel)
        .success
        .value

      val config = new ConfigPurchaseMapping()
      val app = applicationBuilder().build()
      implicit val msgs = messages(app)
      implicit val req = play.api.test.FakeRequest()

      val sections = CheckYourPurchaseDetailsSummary.sections(ua, Some("Euro"), Some("€"), config, showCurrencyRow = true)

      // verify amounts section contains currency display and formatted amounts
      val amounts = sections.find(_._1 == "purchase.checkYourPurchase.purchaseAmounts").value._2
      amounts.map(_._1) must contain(msgs("refundingCurrency.checkYourAnswersLabel"))
      amounts.map(_._1) must contain(msgs("totalPurchaseAmountBeforeVat.checkYourAnswersLabel"))
      amounts.map(_._1) must contain(msgs("totalVatPaid.checkYourAnswersLabel"))
      amounts.map(_._1) must contain(msgs("totalVatClaim.checkYourAnswersLabel"))
    }

    "rowSupplierTaxNumbers should show Not provided when SupplierTaxNumber.Neither present" in {
      val ua = emptyUserAnswers
        .set(SupplierTaxNumberPage, SupplierTaxNumber.Neither)
        .success
        .value

      val app = applicationBuilder().build()
      implicit val msgs = messages(app)

      val row = CheckYourPurchaseDetailsSummary.rowSupplierTaxNumbers(ua)
      row.value._2.value mustBe msgs("site.notProvided")
    }

    "renderSubTypeRow should produce a label when no message key exists" in {
      val ua = emptyUserAnswers.set(PurchaseTypePage, PurchaseType.Fuel).success.value
      val app = applicationBuilder().build()
      implicit val msgs = messages(app)

      val rowOpt = CheckYourPurchaseDetailsSummary.rowPurchaseType(ua)
      rowOpt.value._1 mustBe msgs("purchaseType.heading")
    }

    "rowInvoiceType should show localized label when invoice type stored" in {
      val ua = emptyUserAnswers
        .set(InvoiceTypePage, InvoiceType.StandardInvoice)
        .success
        .value

      val app = applicationBuilder().build()
      running(app) {
        implicit val msgs = messages(app)

        val rowOpt = CheckYourPurchaseDetailsSummary.rowInvoiceType(ua)
        rowOpt.value._2.value mustBe msgs("invoiceType.standardInvoice")
      }
    }

    "currency row in sections" - {
      "must include a currency row when a display name is provided (multi-currency country)" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(RefundingCountryPage, "EE")
          .success
          .value

        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          implicit val msgs: Messages = messages(application)
          implicit val req: play.api.mvc.RequestHeader = play.api.test.FakeRequest()

          val sections = CheckYourPurchaseDetailsSummary.sections(userAnswers,
                                                                  Some(msgs("site.notProvided")),
                                                                  None,
                                                                  new ConfigPurchaseMapping(),
                                                                  showCurrencyRow = true
                                                                 )

          val amountsSection = sections.find(_._1 == "purchase.checkYourPurchase.purchaseAmounts")
          amountsSection mustBe defined
          val rows = amountsSection.value._2

          rows.exists(_._1 == msgs("refundingCurrency.checkYourAnswersLabel")) mustBe true

          val currencyRow = rows.find(_._1 == msgs("refundingCurrency.checkYourAnswersLabel")).value
          currencyRow._3.head._1 mustEqual routes.RefundingCurrencyController.onPageLoad(CheckMode).url
        }
      }
    }

    "rowPurchaseSubTypeLabel" - {
      "must be empty when no subcodes exist for the selected purchase type and country" in {
        val userAnswers = UserAnswers(userAnswersId)
          .set(RefundingCountryPage, "LT")
          .success
          .value
          .set(PurchaseTypePage, PurchaseType.Fuel)
          .success
          .value

        val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

        running(application) {
          implicit val msgs: Messages = messages(application)

          val row = CheckYourPurchaseDetailsSummary.rowPurchaseSubTypeLabel(userAnswers, new ConfigPurchaseMapping())

          row mustBe None
        }
      }
    }

  }
}
