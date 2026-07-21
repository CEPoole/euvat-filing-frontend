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
import utils.{ConfigCurrencyMapping, ConfigLanguageMapping}

@Singleton
class Navigator @Inject() (configCurrencyMapping: ConfigCurrencyMapping, configLanguageMapping: ConfigLanguageMapping) {

  def nextPage(page: Page, mode: Mode, userAnswers: UserAnswers): Call = mode match {
    case NormalMode => normalRoutes(page)(userAnswers)
    case CheckMode  => checkRoutes(page)(userAnswers)
  }

  private val normalRoutes: Page => UserAnswers => Call = {
    case RefundingCountryPage =>
      userAnswers =>
        val maybeCountryCode = userAnswers.get(pages.RefundingCountryPage).orElse {
          userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
            stored.split(",", 2).headOption.getOrElse(stored)
          }
        }

        maybeCountryCode match {
          case Some(code) if configLanguageMapping.languagesFor(code).size <= 1 =>
            if (configCurrencyMapping.requiresCurrencySelection(code)) { routes.RefundingCurrencyController.onPageLoad(NormalMode) }
            else { routes.RefundPeriodController.onPageLoad(NormalMode) }
          case _ => routes.RefundingLanguageController.onPageLoad(NormalMode)
        }
    case RefundingLanguagePage             => userAnswers => navigateFromRefundingLanguagePage(NormalMode)(userAnswers)
    case RefundingCurrencyPage             => userAnswers => navigateFromRefundingCurrencyPage(NormalMode)(userAnswers)
    case RefundPeriodPage                  => _ => routes.ContactDetailsController.onPageLoad(NormalMode)
    case ContactDetailsPage                => _ => routes.BusinessActivityController.onPageLoad(NormalMode)
    case BusinessActivityPage              => userAnswer => navigateFromBusinessActivityPage(NormalMode)(userAnswer)
    case BusinessActivityTwoPage           => userAnswer => navigateFromBusinessActivity2Page(NormalMode)(userAnswer)
    case BusinessActivityCodeThreePage     => _ => routes.BusinessActivityThreeController.onPageLoad()
    case PurchaseTypePage                  => userAnswer => navigateFromPurchaseTypePage(NormalMode)(userAnswer)
    case DescribeItemsOnInvoicePage        => _ => routes.InvoiceTypeController.onPageLoad(NormalMode)
    case InvoiceTypePage                   => userAnswer => navigateFromInvoiceTypePage(NormalMode)(userAnswer)
    case InvoiceNumberPage                 => _ => routes.InvoiceDateController.onPageLoad(NormalMode)
    case InvoiceDatePage                   => _ => routes.SuppliersNameController.onPageLoad(NormalMode)
    case SuppliersNamePage                 => _ => routes.SupplierAddressController.onPageLoad(NormalMode)
    case SupplierAddressPage               => userAnswers => navigateFromSupplierAddressPage(NormalMode)(userAnswers)
    case SupplierTaxNumberPage             => userAnswers => navigateFromSupplierTaxNumberPage(NormalMode)(userAnswers)
    case SimplifiedInvoiceVatRegCheckPage  => userAnswer => navigateFromSimplifiedInvoiceVatRegCheckPage(NormalMode)(userAnswer)
    case SupplierVatRegistrationNumberPage => _ => routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode)
    case SupplierTaxIdentifierNumberPage   => _ => routes.JourneyRecoveryController.onPageLoad()
    case TotalPurchaseAmountBeforeVatPage  => _ => routes.TotalVatPaidController.onPageLoad(NormalMode)
    case TotalVatPaidPage                  => _ => routes.TotalVatClaimController.onPageLoad(NormalMode)
    case TotalVatClaimPage                 => _ => routes.JourneyRecoveryController.onPageLoad()
    case CheckYourStateDetailsPage         => userAnswer => navigateFromCheckYourStateDetailsPage(NormalMode)(userAnswer)
    case _                                 => _ => routes.IndexController.onPageLoad()
  }

  private val checkRoutes: Page => UserAnswers => Call = {
    case RefundingCountryPage =>
      userAnswers =>
        val maybeCountryCode = userAnswers.get(pages.RefundingCountryPage).orElse {
          userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
            stored.split(",", 2).headOption.getOrElse(stored)
          }
        }

        maybeCountryCode match {
          case Some(code) if configLanguageMapping.languagesFor(code).size <= 1 =>
            if (configCurrencyMapping.requiresCurrencySelection(code)) { routes.RefundingCurrencyController.onPageLoad(CheckMode) }
            else { routes.CheckYourClaimDetailsController.onPageLoad() }
          case _ => routes.RefundingLanguageController.onPageLoad(CheckMode)
        }
    case RefundingLanguagePage             => userAnswers => navigateFromRefundingLanguagePage(CheckMode)(userAnswers)
    case RefundingCurrencyPage             => userAnswers => navigateFromRefundingCurrencyPage(CheckMode)(userAnswers)
    case RefundPeriodPage                  => _ => routes.CheckYourClaimDetailsController.onPageLoad()
    case ContactDetailsPage                => _ => routes.CheckYourClaimDetailsController.onPageLoad()
    case BusinessActivityPage              => userAnswer => navigateFromBusinessActivityPage(CheckMode)(userAnswer)
    case BusinessActivityTwoPage           => userAnswer => navigateFromBusinessActivity2Page(CheckMode)(userAnswer)
    case BusinessActivityCodeThreePage     => _ => routes.BusinessActivityThreeController.onPageLoad()
    case PurchaseTypePage                  => userAnswer => navigateFromPurchaseTypePage(CheckMode)(userAnswer)
    case DescribeItemsOnInvoicePage        => _ => routes.InvoiceTypeController.onPageLoad(CheckMode)
    case InvoiceTypePage                   => userAnswer => navigateFromInvoiceTypePage(CheckMode)(userAnswer)
    case InvoiceNumberPage                 => _ => routes.InvoiceDateController.onPageLoad(CheckMode)
    case InvoiceDatePage                   => _ => routes.SuppliersNameController.onPageLoad(CheckMode)
    case SuppliersNamePage                 => _ => routes.SupplierAddressController.onPageLoad(CheckMode)
    case SupplierAddressPage               => userAnswers => navigateFromSupplierAddressPage(CheckMode)(userAnswers)
    case SupplierTaxNumberPage             => userAnswers => navigateFromSupplierTaxNumberPage(CheckMode)(userAnswers)
    case SimplifiedInvoiceVatRegCheckPage  => userAnswer => navigateFromSimplifiedInvoiceVatRegCheckPage(CheckMode)(userAnswer)
    case SupplierVatRegistrationNumberPage => _ => routes.TotalPurchaseAmountBeforeVatController.onPageLoad(CheckMode)
    case SupplierTaxIdentifierNumberPage   => _ => routes.JourneyRecoveryController.onPageLoad()
    case TotalPurchaseAmountBeforeVatPage  => _ => routes.TotalVatPaidController.onPageLoad(CheckMode)
    case TotalVatPaidPage                  => _ => routes.TotalVatClaimController.onPageLoad(CheckMode)
    case TotalVatClaimPage                 => _ => routes.JourneyRecoveryController.onPageLoad()
    case CheckYourStateDetailsPage         => userAnswers => navigateFromCheckYourStateDetailsPage(CheckMode)(userAnswers)
    case _                                 => _ => routes.IndexController.onPageLoad()
  }

  private def navigateFromRefundingLanguagePage(mode: Mode)(userAnswers: UserAnswers): Call = {
    val maybeCountryCode = userAnswers.get(pages.RefundingCountryPage).orElse {
      userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
        stored.split(",", 2).headOption.getOrElse(stored)
      }
    }
    maybeCountryCode match {
      case Some(countryCode) if configCurrencyMapping.requiresCurrencySelection(countryCode) =>
        mode match {
          case NormalMode => routes.RefundingCurrencyController.onPageLoad(mode)
          case CheckMode =>
            if (userAnswers.get(pages.CountryChangedPage).contains(true))
              routes.RefundingCurrencyController.onPageLoad(mode)
            else
              routes.CheckYourClaimDetailsController.onPageLoad()
        }
      case Some(_) =>
        mode match {
          case NormalMode => routes.RefundPeriodController.onPageLoad(mode)
          case CheckMode =>
            if (userAnswers.get(pages.CountryChangedPage).contains(true))
              routes.RefundPeriodController.onPageLoad(CheckMode)
            else
              routes.CheckYourClaimDetailsController.onPageLoad()
        }
      case None =>
        routes.JourneyRecoveryController.onPageLoad()
    }
  }

  private def navigateFromSupplierAddress(mode: Mode): UserAnswers => Call = { userAnswers =>
    val maybeCountryCode = userAnswers.get(pages.RefundingCountryPage).orElse {
      userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
        stored.split(",", 2).headOption.getOrElse(stored)
      }
    }

    maybeCountryCode match {
      case Some("DE") => routes.SupplierTaxIdentifierNumberController.onPageLoad(mode)
      case _          => routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(mode)
    }
  }

  private def navigateFromRefundingCurrencyPage(mode: Mode)(userAnswers: UserAnswers): Call =
    mode match {
      case NormalMode => routes.RefundPeriodController.onPageLoad(NormalMode)
      case CheckMode =>
        if (userAnswers.get(pages.CountryChangedPage).contains(true)) {
          routes.RefundPeriodController.onPageLoad(CheckMode)
        } else {
          routes.CheckYourClaimDetailsController.onPageLoad()
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

  private def navigateFromInvoiceTypePage(mode: Mode)(userAnswers: UserAnswers): Call =
    userAnswers.get(InvoiceTypePage) match {
      case Some(InvoiceType.StandardInvoice) => routes.InvoiceNumberController.onPageLoad(mode)
      case Some(InvoiceType.SimplifiedInvoice) =>
        routes.InvoiceNumberController.onPageLoad(mode)
      case _ => routes.JourneyRecoveryController.onPageLoad()
    }

  private def navigateFromSimplifiedInvoiceVatRegCheckPage(mode: Mode)(userAnswers: UserAnswers): Call =
    userAnswers.get(SimplifiedInvoiceVatRegCheckPage) match {
      case Some(true) =>
        userAnswers.get(InvoiceTypePage) match {
          case Some(InvoiceType.SimplifiedInvoice) => routes.SupplierVatRegistrationNumberController.onPageLoad(mode)
          case _                                   => routes.TotalPurchaseAmountBeforeVatController.onPageLoad(mode)
        }
      case Some(false) => routes.TotalPurchaseAmountBeforeVatController.onPageLoad(mode)
      case _           => routes.JourneyRecoveryController.onPageLoad()
    }

  private def navigateFromPurchaseTypePage(mode: Mode)(userAnswers: UserAnswers): Call =
    userAnswers.get(PurchaseTypePage) match {
      case Some(_) =>
        routes.DescribeItemsOnInvoiceController.onPageLoad(mode) // TODO - route to RA6.0 PurchaseSubCode once built, keyed by Country + Category
      case _ => routes.JourneyRecoveryController.onPageLoad()
    }

  private def navigateFromSupplierAddressPage(mode: Mode)(userAnswers: UserAnswers): Call = {
    val maybeCountryCode = userAnswers.get(pages.RefundingCountryPage).orElse {
      userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
        stored.split(",", 2).headOption.getOrElse(stored)
      }
    }
    maybeCountryCode match {
      case Some("DE") => routes.SupplierTaxNumberController.onPageLoad(mode)
      case _          => routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(mode)
    }
  }

  private def navigateFromSupplierTaxNumberPage(mode: Mode)(userAnswers: UserAnswers): Call =
    userAnswers.get(SupplierTaxNumberPage) match {
      case Some(SupplierTaxNumber.Vatregistrationnumber) =>
        routes.JourneyRecoveryController.onPageLoad() // TODO - link to VAT registration number page once built
      case Some(SupplierTaxNumber.Taxidentifiernumber) =>
        routes.SupplierTaxIdentifierNumberController.onPageLoad(mode)
      case Some(SupplierTaxNumber.Neither) =>
        routes.PurchaseTypeController.onPageLoad(mode)
      case _ => routes.JourneyRecoveryController.onPageLoad()
    }

  private def navigateFromCheckYourStateDetailsPage(mode: Mode)(userAnswers: UserAnswers): Call =
    userAnswers.get(CheckYourStateDetailsPage) match {
      case Some(true)  => routes.JourneyRecoveryController.onPageLoad() // TODO: replace when F8 delete application is in place
      case Some(false) => routes.CheckYourClaimDetailsController.onPageLoad()
      case _           => routes.JourneyRecoveryController.onPageLoad()
    }
}
