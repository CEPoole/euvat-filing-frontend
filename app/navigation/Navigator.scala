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

import javax.inject.{Inject, Singleton}
import play.api.mvc.Call
import controllers.routes
import pages.*
import models.*
import utils.{ConfigCurrencyMapping, ConfigLanguageMapping, ConfigPurchaseMapping, CountryCode}

@Singleton
class Navigator @Inject() (configCurrencyMapping: ConfigCurrencyMapping,
                           configLanguageMapping: ConfigLanguageMapping,
                           configPurchaseMapping: ConfigPurchaseMapping
                          ) {

  def nextPage(page: Page, mode: Mode, userAnswers: UserAnswers): Call = mode match {
    case NormalMode => normalRoutes(page)(userAnswers)
    case CheckMode  => checkRoutes(page)(userAnswers)
  }

  private val normalRoutes: Page => UserAnswers => Call = {
    case RefundingCountryPage              => userAnswers => navigateFromRefundingCountryPage(NormalMode, userAnswers)
    case RefundingLanguagePage             => userAnswers => navigateFromRefundingLanguagePage(NormalMode)(userAnswers)
    case RefundPeriodPage                  => _ => routes.ContactDetailsController.onPageLoad(NormalMode)
    case ContactDetailsPage                => _ => routes.BusinessActivityController.onPageLoad(NormalMode)
    case BusinessActivityPage              => userAnswer => navigateFromBusinessActivityPage(NormalMode)(userAnswer)
    case BusinessActivityTwoPage           => userAnswer => navigateFromBusinessActivity2Page(NormalMode)(userAnswer)
    case BusinessActivityCodeThreePage     => _ => routes.BusinessActivityThreeController.onPageLoad()
    case CheckYourStateDetailsPage         => userAnswer => navigateFromCheckYourStateDetailsPage(NormalMode)(userAnswer)
    case PurchaseTypePage                  => userAnswer => navigateFromPurchaseTypePage(NormalMode)(userAnswer)
    case PurchaseSubCategoryPage           => userAnswers => navigateFromPurchaseSubCategoryPage(NormalMode, userAnswers)
    case DescribeItemsOnInvoicePage        => _ => routes.InvoiceTypeController.onPageLoad(NormalMode)
    case InvoiceTypePage                   => userAnswer => navigateFromInvoiceTypePage(NormalMode)(userAnswer)
    case InvoiceNumberPage                 => _ => routes.InvoiceDateController.onPageLoad(NormalMode)
    case InvoiceDatePage                   => _ => routes.SuppliersNameController.onPageLoad(NormalMode)
    case SuppliersNamePage                 => _ => routes.SupplierAddressController.onPageLoad(NormalMode)
    case SupplierAddressPage               => userAnswers => navigateFromSupplierAddressPage(NormalMode)(userAnswers)
    case SupplierTaxNumberPage             => userAnswers => navigateFromSupplierTaxNumberPage(NormalMode)(userAnswers)
    case SimplifiedInvoiceVatRegCheckPage  => userAnswer => navigateFromSimplifiedInvoiceVatRegCheckPage(NormalMode)(userAnswer)
    case SupplierVatRegistrationNumberPage => userAnswers => navigateToCurrencyOrPurchaseAmount(NormalMode)(userAnswers)
    case SupplierTaxIdentifierNumberPage   => userAnswers => navigateToCurrencyOrPurchaseAmount(NormalMode)(userAnswers)
    case RefundingCurrencyPage             => _ => routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode)
    case TotalPurchaseAmountBeforeVatPage  => _ => routes.TotalVatPaidController.onPageLoad(NormalMode)
    case TotalVatPaidPage                  => _ => routes.TotalVatClaimController.onPageLoad(NormalMode)
    case TotalVatClaimPage                 => _ => routes.JourneyRecoveryController.onPageLoad()
    case _                                 => _ => routes.IndexController.onPageLoad()
  }

  private val checkRoutes: Page => UserAnswers => Call = {
    case RefundingCountryPage              => userAnswers => navigateFromRefundingCountryPage(CheckMode, userAnswers)
    case RefundingLanguagePage             => userAnswers => navigateFromRefundingLanguagePage(CheckMode)(userAnswers)
    case RefundPeriodPage                  => _ => routes.CheckYourClaimDetailsController.onPageLoad()
    case ContactDetailsPage                => _ => routes.CheckYourClaimDetailsController.onPageLoad()
    case BusinessActivityPage              => userAnswer => navigateFromBusinessActivityPage(CheckMode)(userAnswer)
    case BusinessActivityTwoPage           => userAnswer => navigateFromBusinessActivity2Page(CheckMode)(userAnswer)
    case BusinessActivityCodeThreePage     => _ => routes.BusinessActivityThreeController.onPageLoad()
    case CheckYourStateDetailsPage         => userAnswers => navigateFromCheckYourStateDetailsPage(CheckMode)(userAnswers)
    case PurchaseTypePage                  => userAnswer => navigateFromPurchaseTypePage(CheckMode)(userAnswer)
    case PurchaseSubCategoryPage           => userAnswers => navigateFromPurchaseSubCategoryPage(CheckMode, userAnswers)
    case DescribeItemsOnInvoicePage        => _ => routes.InvoiceTypeController.onPageLoad(CheckMode)
    case InvoiceTypePage                   => userAnswer => navigateFromInvoiceTypePage(CheckMode)(userAnswer)
    case InvoiceNumberPage                 => _ => routes.InvoiceDateController.onPageLoad(CheckMode)
    case InvoiceDatePage                   => _ => routes.SuppliersNameController.onPageLoad(CheckMode)
    case SuppliersNamePage                 => _ => routes.SupplierAddressController.onPageLoad(CheckMode)
    case SupplierAddressPage               => userAnswers => navigateFromSupplierAddressPage(CheckMode)(userAnswers)
    case SupplierTaxNumberPage             => userAnswers => navigateFromSupplierTaxNumberPage(CheckMode)(userAnswers)
    case SimplifiedInvoiceVatRegCheckPage  => userAnswer => navigateFromSimplifiedInvoiceVatRegCheckPage(CheckMode)(userAnswer)
    case SupplierVatRegistrationNumberPage => userAnswers => navigateToCurrencyOrPurchaseAmount(CheckMode)(userAnswers)
    case SupplierTaxIdentifierNumberPage   => userAnswers => navigateToCurrencyOrPurchaseAmount(CheckMode)(userAnswers)
    case RefundingCurrencyPage             => _ => routes.TotalPurchaseAmountBeforeVatController.onPageLoad(CheckMode)
    case TotalPurchaseAmountBeforeVatPage  => _ => routes.TotalVatPaidController.onPageLoad(CheckMode)
    case TotalVatPaidPage                  => _ => routes.TotalVatClaimController.onPageLoad(CheckMode)
    case TotalVatClaimPage                 => _ => routes.JourneyRecoveryController.onPageLoad()
    case _                                 => _ => routes.IndexController.onPageLoad()
  }

  private def navigateFromRefundingCountryPage(mode: Mode, userAnswers: UserAnswers) = {
    CountryCode.findCountryCode(userAnswers) match {
      case Some(code) if configLanguageMapping.languagesFor(code).size <= 1 =>
        mode match {
          case NormalMode => routes.RefundPeriodController.onPageLoad(NormalMode)
          case CheckMode  => routes.CheckYourClaimDetailsController.onPageLoad()
        }
      case _ => routes.RefundingLanguageController.onPageLoad(mode)
    }
  }

  private def navigateFromRefundingLanguagePage(mode: Mode)(userAnswers: UserAnswers): Call = {
    CountryCode.findCountryCode(userAnswers) match {
      case Some(_) =>
        mode match {
          case NormalMode => routes.RefundPeriodController.onPageLoad(NormalMode)
          case CheckMode =>
            if (userAnswers.get(CountryChangedPage).contains(true)) {
              routes.RefundPeriodController.onPageLoad(CheckMode)
            } else {
              routes.CheckYourClaimDetailsController.onPageLoad()
            }
        }
      case None => routes.JourneyRecoveryController.onPageLoad()
    }
  }

  private def navigateFromSupplierAddressPage(mode: Mode)(userAnswers: UserAnswers): Call = {
    CountryCode.findCountryCode(userAnswers) match {
      case Some("DE") => routes.SupplierTaxNumberController.onPageLoad(mode)
      case _          => routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(mode)
    }
  }

  private def navigateFromBusinessActivityPage(mode: Mode)(userAnswers: UserAnswers): Call =
    userAnswers.get(BusinessActivityPage) match {
      case Some(true)  => routes.BusinessActivityCodeTwoController.onPageLoad(mode)
      case Some(false) => routes.CheckYourClaimDetailsController.onPageLoad()
      case _           => routes.JourneyRecoveryController.onPageLoad()
    }

  private def navigateFromBusinessActivity2Page(mode: Mode)(userAnswers: UserAnswers): Call =
    userAnswers.get(BusinessActivityTwoPage) match {
      case Some(true)  => routes.BusinessActivityCodeThreeController.onPageLoad(mode)
      case Some(false) => routes.CheckYourClaimDetailsController.onPageLoad()
      case _           => routes.JourneyRecoveryController.onPageLoad()
    }

  private def navigateFromInvoiceTypePage(mode: Mode)(userAnswers: UserAnswers): Call = {
    userAnswers.get(InvoiceTypePage) match {
      case Some(InvoiceType.StandardInvoice)   => routes.InvoiceNumberController.onPageLoad(mode)
      case Some(InvoiceType.SimplifiedInvoice) => routes.InvoiceNumberController.onPageLoad(mode)
      case _                                   => routes.JourneyRecoveryController.onPageLoad()
    }
  }

  private def navigateToCurrencyOrPurchaseAmount(mode: Mode)(userAnswers: UserAnswers): Call = {
    CountryCode.findCountryCode(userAnswers) match {
      case Some(countryCode) if configCurrencyMapping.requiresCurrencySelection(countryCode) =>
        routes.RefundingCurrencyController.onPageLoad(mode)
      case Some(_) => routes.TotalPurchaseAmountBeforeVatController.onPageLoad(mode)
      case None    => routes.JourneyRecoveryController.onPageLoad()
    }
  }

  private def navigateFromSimplifiedInvoiceVatRegCheckPage(mode: Mode)(userAnswers: UserAnswers): Call =
    userAnswers.get(SimplifiedInvoiceVatRegCheckPage) match {
      case Some(true)  => routes.SupplierVatRegistrationNumberController.onPageLoad(mode)
      case Some(false) => navigateToCurrencyOrPurchaseAmount(mode)(userAnswers)
      case _           => routes.JourneyRecoveryController.onPageLoad()
    }

  private def navigateFromPurchaseTypePage(mode: Mode)(userAnswers: UserAnswers): Call =
    userAnswers.get(PurchaseTypePage) match {
      case Some(parent) =>
        CountryCode.findCountryCode(userAnswers) match {
          case Some(country) =>
            val subs = configPurchaseMapping.subcodesFor(country, parent.toString)
            if (subs.nonEmpty) {
              Call("GET", s"/${PurchaseType.slugOf(parent)}")
            } else { routes.InvoiceTypeController.onPageLoad(mode) }
          case _ =>
            // No country present: gracefully route to the Describe Items page so the
            // flow remains usable in tests and UI scenarios where country is set later.
            routes.DescribeItemsOnInvoiceController.onPageLoad(mode)
        }

      case _ => routes.JourneyRecoveryController.onPageLoad()
    }

  private def navigateFromPurchaseSubCategoryPage(mode: Mode, userAnswers: UserAnswers): Call = {
    userAnswers.get(PurchaseTypePage) match {
      case Some(_) => routes.InvoiceTypeController.onPageLoad(mode)
      case _       => routes.JourneyRecoveryController.onPageLoad()
    }
  }

  private def navigateFromSupplierTaxNumberPage(mode: Mode)(userAnswers: UserAnswers): Call =
    userAnswers.get(SupplierTaxNumberPage) match {
      case Some(SupplierTaxNumber.Vatregistrationnumber) =>
        routes.SupplierVatRegistrationNumberController.onPageLoad(mode)
      case Some(SupplierTaxNumber.Taxidentifiernumber) =>
        routes.SupplierTaxIdentifierNumberController.onPageLoad(mode)
      case Some(SupplierTaxNumber.Neither) =>
        navigateToCurrencyOrPurchaseAmount(mode)(userAnswers)
      case _ => routes.JourneyRecoveryController.onPageLoad()
    }

  private def navigateFromCheckYourStateDetailsPage(mode: Mode)(userAnswers: UserAnswers): Call =
    userAnswers.get(CheckYourStateDetailsPage) match {
      case Some(true)  => routes.JourneyRecoveryController.onPageLoad() // TODO: replace when F8 delete application is in place
      case Some(false) => routes.CheckYourClaimDetailsController.onPageLoad()
      case _           => routes.JourneyRecoveryController.onPageLoad()
    }

}
