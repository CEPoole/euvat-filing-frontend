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

package navigation

import base.SpecBase
import controllers.routes
import models.*
import pages.*
import utils.ConfigCurrencyMapping
import utils.ConfigLanguageMapping
import play.api.Configuration
import com.typesafe.config.ConfigFactory

class NavigatorSpec extends SpecBase {

  val navigator = new Navigator(
    new ConfigCurrencyMapping(
      Configuration(
        ConfigFactory.parseString("""
          currency.mapping {
            BG = ["euro|EUR|€", "bulgarianLev|BGN|лв"]
            EE = ["euro|EUR|€", "estonianKroon|EEK|kr"]
            AT = ["euro|EUR|€"]
          }
        """)
      )
    ),
    new ConfigLanguageMapping(
      Configuration(
        ConfigFactory.parseString("""
          language.mapping = {
            AT = ["german", "english"]
            BE = ["english", "german", "french", "dutch"]
            CZ = ["czech"]
          }
        """)
      )
    )
  )
  val userAnswers: UserAnswers = UserAnswers("id")

  "Navigator" - {

    "in Normal mode" - {
      "must go from a page that doesn't exist in the route map to Index" in {
        case object UnknownPage extends Page
        navigator.nextPage(UnknownPage, NormalMode, userAnswers) mustBe routes.IndexController.onPageLoad()
      }

      "must go from RefundingCountryPage to RefundingLanguageController" in {
        navigator.nextPage(pages.RefundingCountryPage, NormalMode, userAnswers) mustBe
          routes.RefundingLanguageController.onPageLoad(NormalMode)
      }

      "must go from RefundingLanguagePage to RefundingCurrencyController if country has two currencies" in {
        val ua = userAnswers.set(pages.RefundingCountryPage, "BG").success.value
        navigator.nextPage(pages.RefundingLanguagePage, NormalMode, ua) mustBe
          routes.RefundingCurrencyController.onPageLoad(NormalMode)
      }

      "must go from RefundingLanguagePage to JourneyRecoveryController if country is missing" in {
        navigator.nextPage(pages.RefundingLanguagePage, NormalMode, userAnswers) mustBe
          routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from RefundingLanguagePage to RefundPeriodController if country has one currency" in {
        val ua = userAnswers.set(pages.RefundingCountryPage, "AT").success.value
        navigator.nextPage(pages.RefundingLanguagePage, NormalMode, ua) mustBe
          routes.RefundPeriodController.onPageLoad(NormalMode)
      }

      "must go from RefundingCurrencyPage to RefundPeriodController" in {
        navigator.nextPage(pages.RefundingCurrencyPage, NormalMode, userAnswers) mustBe
          routes.RefundPeriodController.onPageLoad(NormalMode)
      }

      "must go from RefundPeriodPage to ContactDetailsController" in {
        navigator.nextPage(pages.RefundPeriodPage, NormalMode, userAnswers) mustBe
          routes.ContactDetailsController.onPageLoad(NormalMode)
      }

      "must go from ContactDetailsPage to BusinessActivityController" in {
        navigator.nextPage(ContactDetailsPage, NormalMode, userAnswers) mustBe
          routes.BusinessActivityController.onPageLoad(NormalMode)
      }

      "must go from BusinessActivityPage to BusinessActivityCodeTwoController if yes selected" in {
        val ua = userAnswers.set(BusinessActivityPage, true).success.value
        navigator.nextPage(BusinessActivityPage, NormalMode, ua) mustBe
          routes.BusinessActivityCodeTwoController.onPageLoad(NormalMode)
      }

      "must go from BusinessActivityPage to CheckYourClaimDetailsPage if no selected" in {
        val ua = userAnswers.set(BusinessActivityPage, false).success.value
        navigator.nextPage(BusinessActivityPage, NormalMode, ua) mustBe
          routes.CheckYourClaimDetailsController.onPageLoad()
      }

      "must go from BusinessActivityTwoPage to BusinessActivityCodeThreeController if yes selected" in {
        val ua = userAnswers.set(BusinessActivityTwoPage, true).success.value
        navigator.nextPage(BusinessActivityTwoPage, NormalMode, ua) mustBe
          routes.BusinessActivityCodeThreeController.onPageLoad(NormalMode)
      }

      "must go from InvoiceTypePage to InvoiceNumberController if standard invoice is selected" in {
        val ua = userAnswers.set(InvoiceTypePage, InvoiceType.StandardInvoice).success.value
        navigator.nextPage(InvoiceTypePage, NormalMode, ua) mustBe
          routes.InvoiceNumberController.onPageLoad(NormalMode)
      }

      "must go from InvoiceTypePage to JourneyRecoveryController if simplified invoice is selected" in {
        val ua = userAnswers.set(InvoiceTypePage, InvoiceType.SimplifiedInvoice).success.value
        navigator.nextPage(InvoiceTypePage, NormalMode, ua) mustBe
          routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from BusinessActivityTwoPage to CheckYourClaimDetailsPage if no selected" in {
        val ua = userAnswers.set(BusinessActivityTwoPage, false).success.value
        navigator.nextPage(BusinessActivityTwoPage, NormalMode, ua) mustBe
          routes.CheckYourClaimDetailsController.onPageLoad()
      }

      "must go from BusinessActivityCodeThreePage to BusinessActivityThreeController" in {
        navigator.nextPage(BusinessActivityCodeThreePage, NormalMode, userAnswers) mustBe
          routes.BusinessActivityThreeController.onPageLoad()
      }

      "must go from PurchaseTypePage to DescribeItemsOnInvoiceController" in {
        val ua = userAnswers.set(PurchaseTypePage, PurchaseType.values.head).success.value
        navigator.nextPage(PurchaseTypePage, NormalMode, ua) mustBe
          routes.DescribeItemsOnInvoiceController.onPageLoad(NormalMode)
      }

      "must go from PurchaseTypePage to JourneyRecoveryController if no answer is present" in {
        navigator.nextPage(PurchaseTypePage, NormalMode, userAnswers) mustBe
          routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from DescribeItemsOnInvoicePage to InvoiceTypeController" in {
        navigator.nextPage(DescribeItemsOnInvoicePage, NormalMode, userAnswers) mustBe
          routes.InvoiceTypeController.onPageLoad(NormalMode)
      }

      "must go from InvoiceNumberPage to InvoiceDateController" in {
        navigator.nextPage(InvoiceNumberPage, NormalMode, userAnswers) mustBe
          routes.InvoiceDateController.onPageLoad(NormalMode)
      }

      "must go from InvoiceDatePage to SuppliersNameController" in {
        navigator.nextPage(InvoiceDatePage, NormalMode, userAnswers) mustBe
          routes.SuppliersNameController.onPageLoad(NormalMode)
      }

      "must go from SuppliersNamePage to SupplierAddressController" in {
        navigator.nextPage(SuppliersNamePage, NormalMode, userAnswers) mustBe routes.SupplierAddressController.onPageLoad(NormalMode)
      }

      "must go from SupplierAddressPage to SupplierTaxNumberController if country is Germany" in {
        val ua = userAnswers.set(RefundingCountryPage, "DE").success.value
        navigator.nextPage(SupplierAddressPage, NormalMode, ua) mustBe routes.SupplierTaxNumberController.onPageLoad(NormalMode)
      }

      "must go from SupplierAddressPage to SimplifiedInvoiceVatRegCheckController if country is not Germany" in {
        val ua = userAnswers.set(RefundingCountryPage, "FR").success.value
        navigator.nextPage(SupplierAddressPage, NormalMode, ua) mustBe routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(NormalMode)
      }

      "must go from SupplierAddressPage to SimplifiedInvoiceVatRegCheckController if country is missing" in {
        navigator.nextPage(SupplierAddressPage, NormalMode, userAnswers) mustBe routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(NormalMode)
      }

      "must go from SupplierTaxNumberPage to JourneyRecoveryController if VAT registration number is selected" in {
        val ua = userAnswers.set(SupplierTaxNumberPage, SupplierTaxNumber.Vatregistrationnumber).success.value
        navigator.nextPage(SupplierTaxNumberPage, NormalMode, ua) mustBe routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from SupplierTaxNumberPage to SupplierTaxIdentifierNumberController if tax identifier number is selected" in {
        val ua = userAnswers.set(SupplierTaxNumberPage, SupplierTaxNumber.Taxidentifiernumber).success.value
        navigator.nextPage(SupplierTaxNumberPage, NormalMode, ua) mustBe routes.SupplierTaxIdentifierNumberController.onPageLoad(NormalMode)
      }

      "must go from SupplierTaxNumberPage to PurchaseTypeController if neither is selected" in {
        val ua = userAnswers.set(SupplierTaxNumberPage, SupplierTaxNumber.Neither).success.value
        navigator.nextPage(SupplierTaxNumberPage, NormalMode, ua) mustBe
          routes.PurchaseTypeController.onPageLoad(NormalMode)
      }

      "must go from SupplierTaxNumberPage to JourneyRecoveryController if no answer is present" in {
        navigator.nextPage(SupplierTaxNumberPage, NormalMode, userAnswers) mustBe routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from SupplierVatRegistrationNumberPage to TotalPurchaseAmountBeforeVatController" in {
        navigator.nextPage(SupplierVatRegistrationNumberPage, NormalMode, userAnswers) mustBe
          routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode)
      }

      "must go from TotalPurchaseAmountBeforeVatPage to TotalVatPaidController" in {
        navigator.nextPage(TotalPurchaseAmountBeforeVatPage, NormalMode, userAnswers) mustBe
          routes.TotalVatPaidController.onPageLoad(NormalMode)
      }

      "must go from TotalVatPaidPage to TotalVatClaimController" in {
        navigator.nextPage(TotalVatPaidPage, NormalMode, userAnswers) mustBe
          routes.TotalVatClaimController.onPageLoad(NormalMode)
      }

      "must go from TotalVatClaimPage to JourneyRecoveryController" in {
        navigator.nextPage(TotalVatClaimPage, NormalMode, userAnswers) mustBe
          routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from SimplifiedInvoiceVatRegCheckPage to TotalPurchaseAmountBeforeVatController if no selected" in {
        val ua = userAnswers.set(SimplifiedInvoiceVatRegCheckPage, false).success.value
        navigator.nextPage(SimplifiedInvoiceVatRegCheckPage, NormalMode, ua) mustBe
          routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode)
      }

      "must go from SimplifiedInvoiceVatRegCheckPage to SupplierVatRegistrationNumberController if yes selected and invoice type is simplified" in {
        val ua =
          userAnswers.set(SimplifiedInvoiceVatRegCheckPage, true).success.value.set(InvoiceTypePage, InvoiceType.SimplifiedInvoice).success.value
        navigator.nextPage(SimplifiedInvoiceVatRegCheckPage, NormalMode, ua) mustBe
          routes.SupplierVatRegistrationNumberController.onPageLoad(NormalMode)
      }

      "must go from CheckYourStateDetailsPage to CheckYourClaimDetailsController if no selected" in {
        val ua = userAnswers.set(CheckYourStateDetailsPage, false).success.value
        navigator.nextPage(CheckYourStateDetailsPage, NormalMode, ua) mustBe
          routes.CheckYourClaimDetailsController.onPageLoad()
      }

      "must go from CheckYourStateDetailsPage to JourneyRecoveryController if yes selected" in {
        val ua = userAnswers.set(CheckYourStateDetailsPage, true).success.value
        navigator.nextPage(CheckYourStateDetailsPage, NormalMode, ua) mustBe
          routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from CheckYourStateDetailsPage to JourneyRecoveryController if no answer is given" in {
        navigator.nextPage(CheckYourStateDetailsPage, NormalMode, userAnswers) mustBe
          routes.JourneyRecoveryController.onPageLoad()
      }
    }

    "in Check mode" - {
      "must go from a page that doesn't exist in the edit route map to IndexController" in {
        case object UnknownPage extends Page
        navigator.nextPage(UnknownPage, CheckMode, userAnswers) mustBe routes.IndexController.onPageLoad()
      }

      "must go from RefundingCountryPage to RefundingLanguageController" in {
        navigator.nextPage(pages.RefundingCountryPage, CheckMode, userAnswers) mustBe
          routes.RefundingLanguageController.onPageLoad(CheckMode)
      }

      "must go from RefundingLanguagePage to CheckYourClaimDetailsController in CheckMode if country has two currencies and CountryChangedPage is not set" in {
        val ua = userAnswers.set(pages.RefundingCountryPage, "BG").success.value
        navigator.nextPage(pages.RefundingLanguagePage, CheckMode, ua) mustBe
          routes.CheckYourClaimDetailsController.onPageLoad()
      }

      "must go from RefundingLanguagePage to RefundingCurrencyController in CheckMode if country has two currencies and CountryChangedPage is true" in {
        val ua = userAnswers
          .set(pages.RefundingCountryPage, "BG")
          .success
          .value
          .set(pages.CountryChangedPage, true)
          .success
          .value
        navigator.nextPage(pages.RefundingLanguagePage, CheckMode, ua) mustBe
          routes.RefundingCurrencyController.onPageLoad(CheckMode)
      }

      "must go from RefundingLanguagePage to CheckYourClaimDetailsController if country has one currency" in {
        val ua = userAnswers.set(pages.RefundingCountryPage, "AT").success.value
        navigator.nextPage(pages.RefundingLanguagePage, CheckMode, ua) mustBe
          routes.CheckYourClaimDetailsController.onPageLoad()
      }

      "must go from RefundPeriodPage to CheckYourClaimDetailsController" in {
        navigator.nextPage(pages.RefundPeriodPage, CheckMode, userAnswers) mustBe
          routes.CheckYourClaimDetailsController.onPageLoad()
      }

      "must go from RefundingCurrencyPage to RefundPeriodController in CheckMode if CountryChangedPage is true" in {
        val ua = userAnswers.set(pages.CountryChangedPage, true).success.value
        navigator.nextPage(pages.RefundingCurrencyPage, CheckMode, ua) mustBe
          routes.RefundPeriodController.onPageLoad(CheckMode)
      }

      "must go from RefundingCurrencyPage to CheckYourClaimDetailsController in CheckMode if CountryChangedPage is not set" in {
        navigator.nextPage(pages.RefundingCurrencyPage, CheckMode, userAnswers) mustBe
          routes.CheckYourClaimDetailsController.onPageLoad()
      }

      "must go from RefundingLanguagePage to RefundPeriodController in CheckMode if country has one currency and CountryChangedPage is true" in {
        val ua = userAnswers
          .set(pages.RefundingCountryPage, "AT")
          .success
          .value
          .set(pages.CountryChangedPage, true)
          .success
          .value
        navigator.nextPage(pages.RefundingLanguagePage, CheckMode, ua) mustBe
          routes.RefundPeriodController.onPageLoad(CheckMode)
      }

      "must go from ContactDetailsPage to CheckYourClaimDetailsController" in {
        navigator.nextPage(ContactDetailsPage, CheckMode, userAnswers) mustBe
          routes.CheckYourClaimDetailsController.onPageLoad()
      }

      "must go from BusinessActivityPage to BusinessActivityCodeTwoController if yes selected" in {
        val ua = userAnswers.set(BusinessActivityPage, true).success.value
        navigator.nextPage(BusinessActivityPage, CheckMode, ua) mustBe
          routes.BusinessActivityCodeTwoController.onPageLoad(CheckMode)
      }

      "must go from BusinessActivityPage to CheckYourClaimDetailsPage if no selected" in {
        val ua = userAnswers.set(BusinessActivityPage, false).success.value
        navigator.nextPage(BusinessActivityPage, CheckMode, ua) mustBe
          routes.CheckYourClaimDetailsController.onPageLoad()
      }

      "must go from BusinessActivityTwoPage to BusinessActivityCodeThreeController if yes selected" in {
        val ua = userAnswers.set(BusinessActivityTwoPage, true).success.value
        navigator.nextPage(BusinessActivityTwoPage, CheckMode, ua) mustBe
          routes.BusinessActivityCodeThreeController.onPageLoad(CheckMode)
      }

      "must go from BusinessActivityTwoPage to CheckYourClaimDetailsPage if no selected" in {
        val ua = userAnswers.set(BusinessActivityTwoPage, false).success.value
        navigator.nextPage(BusinessActivityTwoPage, CheckMode, ua) mustBe
          routes.CheckYourClaimDetailsController.onPageLoad()
      }

      "must go from BusinessActivityCodeThreePage to BusinessActivityThreeController" in {
        navigator.nextPage(BusinessActivityCodeThreePage, CheckMode, userAnswers) mustBe
          routes.BusinessActivityThreeController.onPageLoad()
      }

      "must go from InvoiceTypePage to InvoiceNumberController if standard invoice is selected" in {
        val ua = userAnswers.set(InvoiceTypePage, InvoiceType.StandardInvoice).success.value
        navigator.nextPage(InvoiceTypePage, CheckMode, ua) mustBe
          routes.InvoiceNumberController.onPageLoad(CheckMode)
      }

      "must go from InvoiceTypePage to JourneyRecoveryController if simplified invoice is selected" in {
        val ua = userAnswers.set(InvoiceTypePage, InvoiceType.SimplifiedInvoice).success.value
        navigator.nextPage(InvoiceTypePage, CheckMode, ua) mustBe
          routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from PurchaseTypePage to DescribeItemsOnInvoiceController" in {
        val ua = userAnswers.set(PurchaseTypePage, PurchaseType.values.head).success.value
        navigator.nextPage(PurchaseTypePage, CheckMode, ua) mustBe
          routes.DescribeItemsOnInvoiceController.onPageLoad(CheckMode)
      }

      "must go from PurchaseTypePage to JourneyRecoveryController if no answer is present" in {
        navigator.nextPage(PurchaseTypePage, CheckMode, userAnswers) mustBe
          routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from DescribeItemsOnInvoicePage to InvoiceTypeController in CheckMode" in {
        navigator.nextPage(DescribeItemsOnInvoicePage, CheckMode, userAnswers) mustBe
          routes.InvoiceTypeController.onPageLoad(CheckMode)
      }

      "must go from InvoiceNumberPage to InvoiceDateController in CheckMode" in {
        navigator.nextPage(InvoiceNumberPage, CheckMode, userAnswers) mustBe
          routes.InvoiceDateController.onPageLoad(CheckMode)
      }

      "must go from InvoiceDatePage to SuppliersNameController in CheckMode" in {
        navigator.nextPage(InvoiceDatePage, CheckMode, userAnswers) mustBe
          routes.SuppliersNameController.onPageLoad(CheckMode)
      }

      "must go from SuppliersNamePage to SupplierAddressController in CheckMode" in {
        navigator.nextPage(SuppliersNamePage, CheckMode, userAnswers) mustBe routes.SupplierAddressController.onPageLoad(CheckMode)
      }

      "must go from SupplierAddressPage to SupplierTaxNumberController in CheckMode when country is DE" in {
        val ua = userAnswers.set(RefundingCountryPage, "DE").success.value
        navigator.nextPage(SupplierAddressPage, CheckMode, ua) mustBe routes.SupplierTaxNumberController.onPageLoad(CheckMode)
      }

      "must go from SupplierAddressPage to SimplifiedInvoiceVatRegCheckController in CheckMode when country is not DE" in {
        val ua = userAnswers.set(RefundingCountryPage, "FR").success.value
        navigator.nextPage(SupplierAddressPage, CheckMode, ua) mustBe routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(CheckMode)
      }

      "must go from SupplierAddressPage to SimplifiedInvoiceVatRegCheckController if country is missing" in {
        navigator.nextPage(SupplierAddressPage, CheckMode, userAnswers) mustBe
          routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(CheckMode)
      }

      "must go from SupplierTaxNumberPage to JourneyRecoveryController in CheckMode when VAT registration number selected" in {
        val ua = userAnswers.set(SupplierTaxNumberPage, SupplierTaxNumber.Vatregistrationnumber).success.value
        navigator.nextPage(SupplierTaxNumberPage, CheckMode, ua) mustBe routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from SupplierTaxNumberPage to SupplierTaxIdentifierNumberController in CheckMode when tax identifier selected" in {
        val ua = userAnswers.set(SupplierTaxNumberPage, SupplierTaxNumber.Taxidentifiernumber).success.value
        navigator.nextPage(SupplierTaxNumberPage, CheckMode, ua) mustBe routes.SupplierTaxIdentifierNumberController.onPageLoad(CheckMode)
      }

      "must go from SupplierTaxNumberPage to JourneyRecoveryController in CheckMode when no answer present" in {
        navigator.nextPage(SupplierTaxNumberPage, CheckMode, userAnswers) mustBe routes.JourneyRecoveryController.onPageLoad()
      }

        navigator.nextPage(SuppliersNamePage, CheckMode, userAnswers) mustBe
          routes.SupplierAddressController.onPageLoad(CheckMode)
      }

      "must go from SupplierAddressPage to SupplierTaxNumberController if country is Germany" in {
        val ua = userAnswers.set(pages.RefundingCountryPage, "DE").success.value
        navigator.nextPage(SupplierAddressPage, CheckMode, ua) mustBe
          routes.SupplierTaxNumberController.onPageLoad(CheckMode)
      }

      "must go from SupplierAddressPage to SimplifiedInvoiceVatRegCheckController if country is not Germany" in {
        val ua = userAnswers.set(pages.RefundingCountryPage, "AT").success.value
        navigator.nextPage(SupplierAddressPage, CheckMode, ua) mustBe
          routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(CheckMode)
      }

      "must go from SupplierAddressPage to SimplifiedInvoiceVatRegCheckController if country is missing" in {
        navigator.nextPage(SupplierAddressPage, CheckMode, userAnswers) mustBe
          routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(CheckMode)
      }

      "must go from SupplierTaxNumberPage to JourneyRecoveryController if VAT registration number is selected" in {
        val ua = userAnswers.set(SupplierTaxNumberPage, SupplierTaxNumber.Vatregistrationnumber).success.value
        navigator.nextPage(SupplierTaxNumberPage, CheckMode, ua) mustBe
          routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from SupplierTaxNumberPage to SupplierTaxIdentifierNumberController if tax identifier number is selected" in {
        val ua = userAnswers.set(SupplierTaxNumberPage, SupplierTaxNumber.Taxidentifiernumber).success.value
        navigator.nextPage(SupplierTaxNumberPage, CheckMode, ua) mustBe
          routes.SupplierTaxIdentifierNumberController.onPageLoad(CheckMode)
      }

      "must go from SupplierTaxNumberPage to PurchaseTypeController if neither is selected" in {
        val ua = userAnswers.set(SupplierTaxNumberPage, SupplierTaxNumber.Neither).success.value
        navigator.nextPage(SupplierTaxNumberPage, CheckMode, ua) mustBe
          routes.PurchaseTypeController.onPageLoad(CheckMode)
      }

      "must go from SupplierTaxNumberPage to JourneyRecoveryController if no answer is present" in {
        navigator.nextPage(SupplierTaxNumberPage, CheckMode, userAnswers) mustBe
          routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from SupplierVatRegistrationNumberPage to TotalPurchaseAmountBeforeVatController in CheckMode" in {
        navigator.nextPage(SupplierVatRegistrationNumberPage, CheckMode, userAnswers) mustBe
          routes.TotalPurchaseAmountBeforeVatController.onPageLoad(CheckMode)
      }

      "must go from TotalPurchaseAmountBeforeVatPage to TotalVatPaidController in CheckMode" in {
        navigator.nextPage(TotalPurchaseAmountBeforeVatPage, CheckMode, userAnswers) mustBe
          routes.TotalVatPaidController.onPageLoad(CheckMode)
      }

      "must go from TotalVatPaidPage to TotalVatClaimController in CheckMode" in {
        navigator.nextPage(TotalVatPaidPage, CheckMode, userAnswers) mustBe
          routes.TotalVatClaimController.onPageLoad(CheckMode)
      }

      "must go from TotalVatClaimPage to JourneyRecoveryController in CheckMode" in {
        navigator.nextPage(TotalVatClaimPage, CheckMode, userAnswers) mustBe
          routes.JourneyRecoveryController.onPageLoad()
      }

      "must go from SimplifiedInvoiceVatRegCheckPage to TotalPurchaseAmountBeforeVatController if no selected" in {
        val ua = userAnswers.set(SimplifiedInvoiceVatRegCheckPage, false).success.value
        navigator.nextPage(SimplifiedInvoiceVatRegCheckPage, CheckMode, ua) mustBe
          routes.TotalPurchaseAmountBeforeVatController.onPageLoad(CheckMode)
      }

      "must go from CheckYourStateDetailsPage to CheckYourClaimDetailsController in CheckMode if no selected" in {
        val ua = userAnswers.set(CheckYourStateDetailsPage, false).success.value
        navigator.nextPage(CheckYourStateDetailsPage, CheckMode, ua) mustBe
          routes.CheckYourClaimDetailsController.onPageLoad()
      }
    }
  }
}
