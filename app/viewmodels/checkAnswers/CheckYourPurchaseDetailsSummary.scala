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

import controllers.routes
import utils.ConfigPurchaseMapping
import models.{CheckMode, UserAnswers}
import pages.*
import play.api.i18n.{Lang, Messages}
import play.api.mvc.RequestHeader
import utils.MountPrefix
import viewmodels.govuk.summarylist.*

object CheckYourPurchaseDetailsSummary {

  type Row = (String, Option[String], Seq[(String, String, String)])

  def rowPurchaseType(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(PurchaseTypePage).map { pt =>
      val value = messages(s"purchaseType.${pt.toString}")
      val url = routes.PurchaseTypeController.onPageLoad(CheckMode).url
      (
        messages("purchaseType.checkYourAnswersLabel"),
        Some(value),
        Seq((url, "site.change", "purchaseType.change.hidden"))
      )
    }

  def rowPurchaseSubTypeLabel(answers: UserAnswers, config: ConfigPurchaseMapping)(implicit messages: Messages): Option[Row] = {
    // If the resolved country + parent has no subcodes configured then there
    // is no sub-type selection to show and the row must be suppressed.
    answers.get(PurchaseTypePage) match {
      case None => None
      case Some(pt) =>
        val parentKey = pt.toString

        val countryOpt = answers.get(RefundingCountryPage).orElse {
          answers.get(RefundingCountryNamePage).map { stored =>
            val parts = stored.split(",", 2).map(_.trim)
            if (parts.length > 1) parts.last else stored
          }
        }

        val hasSubcodes = countryOpt
          .flatMap { c =>
            try Some(config.subcodesFor(c, parentKey).nonEmpty)
            catch { case _: Throwable => None }
          }
          .getOrElse(true)

        if (!hasSubcodes) None
        else
          // Preserve existing logic for rendering or suppressing the row when
          // a sentinel 'None' or a bypass case applies.
          answers.get(PurchaseSubTypePage) match {
            case Some(v) if v == ConfigPurchaseMapping.NoneValue || v.split("\\.").lastOption.contains("99") =>
              // Need to check whether this was the controller-bypass case for the
              // country+parent: only hide when the mapping for that country+parent
              // contained exactly one option whose last segment == "99".
              val singleBypass = countryOpt.flatMap { c =>
                try {
                  val opts = config.subcodesFor(c, parentKey)
                  if (opts.nonEmpty && opts.size == 1) Some(opts.head._1) else None
                } catch { case _: Throwable => None }
              }

              singleBypass match {
                case Some(singleCode) if singleCode.split("\\.").lastOption.contains("99") => None
                case _                                                                     => renderSubTypeRow(answers, pt)
              }

            case _ => answers.get(PurchaseTypePage).flatMap(renderSubTypeRow(answers, _))
          }
    }
  }

  private def renderSubTypeRow(answers: UserAnswers, pt: models.PurchaseType)(implicit messages: Messages): Option[Row] = {
    val parentSlug = models.PurchaseType.slugOf(pt)
    val msgKey = s"purchase.subType.$parentSlug"

    val keyLabel = if (messages.isDefinedAt(msgKey)) messages(msgKey) else parentSlug.replace('-', ' ').capitalize

    // value should come from PurchaseSubTypeLabelPage in session
      val valueOpt: Option[String] = answers.get(PurchaseSubTypeLabelPage)
      // If the stored label is the None sentinel, display Not provided instead
      val displayValueOpt: Option[String] = valueOpt.map(v => if (v == ConfigPurchaseMapping.NoneValue) messages("site.notProvided") else v)

    val url = controllers.purchase.routes.PurchaseSubTypeController.onPageLoad(parentSlug, CheckMode).url

      Some((keyLabel, displayValueOpt, Seq((url, "site.change", "purchase.subType.change.hidden"))))
  }

  def rowPurchaseSubCategoryLabel(answers: UserAnswers)(implicit messages: Messages, request: RequestHeader): Option[Row] =
    // Build a humanised heading from the slug mapping in PurchaseSubCategoryType
    // and show the stored sub-category label as the value.
    for {
      pt    <- answers.get(PurchaseTypePage)
      code  <- answers.get(PurchaseSubCategoryPage)
      label <- answers.get(PurchaseSubCategoryLabelPage)
    } yield {
      val parentKey = pt.toString

      // try to resolve an explicit slug; if none, iteratively trim last segment
      // e.g. 7.1.2 -> try 7.1.2, then 7.1 -> mapping exists for 7.1 -> who-food-drink-for
      def findSlug(pk: String, c: String): String = {
        def loop(curr: String): Option[String] =
          models.PurchaseSubCategoryType.slugFor(pk, curr) match {
            case s @ Some(_) => s
            case None        => if (curr.contains('.')) loop(curr.substring(0, curr.lastIndexOf('.'))) else None
          }

        loop(c).getOrElse(models.PurchaseSubCategoryType.pathFor(pk, c))
      }

      // If the stored sub-category code is the special NoneValue sentinel,
      // resolve a slug based on the selected sub-type instead so the CYA
      // change link points to the appropriate parent-specific edit page.
      val codeToResolve = if (code == ConfigPurchaseMapping.NoneValue) answers.get(PurchaseSubTypePage).getOrElse(code) else code
      val slug = findSlug(parentKey, codeToResolve)
      val msgKey = s"purchase.subCategory.$slug"
      val keyLabel = if (messages.isDefinedAt(msgKey)) messages(msgKey) else slug.replace('-', ' ').capitalize

      // Display "Not provided" when the stored label is the None sentinel.
      val displayValue = if (label == ConfigPurchaseMapping.NoneValue) messages("site.notProvided") else label

      // Build a change-* URL for CheckMode using the resolved slug and
      // include the configured mount prefix so the link points to the
      // externally mounted context (e.g. "/file-eu-vat"). Use the implicit
      // RequestHeader to compute the mount via `MountPrefix.get`.
      val mount = MountPrefix.get
      val url = if (mount.isEmpty) s"/change-$slug" else s"$mount/change-$slug"

      (keyLabel, Some(displayValue), Seq((url, "site.change", "purchase.subCategory.change.hidden")))
    }

  def rowInvoiceType(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(InvoiceTypePage).map { it =>
      val url = routes.InvoiceTypeController.onPageLoad(CheckMode).url

      // Try to resolve a localized label first. InvoiceType.toString may be
      // a space-separated name (e.g. "standard invoice"). Message keys use
      // a camelCase suffix (e.g. "standardInvoice"). Convert to that form
      // and fallback to a title-cased raw value if no message key exists.
      val parts = it.toString.split("\\s+").toSeq.filter(_.nonEmpty)
      val keySuffix = parts.headOption.map { first =>
        first + parts.drop(1).map(_.capitalize).mkString("")
      }.getOrElse(it.toString)

      val display = if (messages.isDefinedAt(s"invoiceType.$keySuffix")) {
        messages(s"invoiceType.$keySuffix")
      } else {
        parts.map(_.capitalize).mkString(" ")
      }

      (messages("invoiceType.checkYourAnswersLabel"), Some(display), Seq((url, "site.change", "invoiceType.change.hidden")))
    }

  def rowInvoiceNumber(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(InvoiceNumberPage).map { num =>
      val url = routes.InvoiceNumberController.onPageLoad(CheckMode).url
      (messages("invoiceNumber.checkYourAnswersLabel"), Some(num), Seq((url, "site.change", "invoiceNumber.change.hidden")))
    }

  def rowInvoiceDate(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(InvoiceDatePage).map { date =>
      val url = routes.InvoiceDateController.onPageLoad(CheckMode).url
      implicit val lang: Lang = messages.lang
      (messages("invoiceDate.checkYourAnswersLabel"),
       Some(date.format(utils.DateTimeFormats.dateTimeFormat())),
       Seq((url, "site.change", "invoiceDate.change.hidden"))
      )
    }

  def rowDescribeItems(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(DescribeItemsOnInvoicePage).map { desc =>
      val url = controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(CheckMode).url
      (messages("describeItemsOnInvoice.checkYourAnswersLabel"), Some(desc), Seq((url, "site.change", "describeItemsOnInvoice.change.hidden")))
    }

  def rowSupplierName(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(SuppliersNamePage).map { name =>
      val url = routes.SuppliersNameController.onPageLoad(CheckMode).url
      (messages("suppliersName.checkYourAnswersLabel"), Some(name), Seq((url, "site.change", "suppliersName.change.hidden")))
    }

  def rowSupplierAddress(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(SupplierAddressPage).map { addr =>
      val url = routes.SupplierAddressController.onPageLoad(CheckMode).url
      val lines = Seq(Some(addr.line1), addr.line2, addr.line3).flatten.mkString("<br>")
      (messages("supplierAddress.checkYourAnswersLabel"), Some(lines), Seq((url, "site.change", "supplierAddress.change.hidden")))
    }

  def rowSupplierVatRegCheck(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(SimplifiedInvoiceVatRegCheckPage).map { v =>
      val url = routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(CheckMode).url
      (messages("simplifiedInvoiceVatRegCheck.checkYourAnswersLabel"),
       Some(if (v) messages("site.yes") else messages("site.no")),
       Seq((url, "site.change", "simplifiedInvoiceVatRegCheck.change.hidden"))
      )
    }

  def rowSupplierVatRegNumber(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(SupplierVatRegistrationNumberPage).map { num =>
      val url = routes.SupplierVatRegistrationNumberController.onPageLoad(CheckMode).url
      (messages("supplierVatRegistrationNumber.checkYourAnswersLabel"),
       Some(num),
       Seq((url, "site.change", "supplierVatRegistrationNumber.change.hidden"))
      )
    }

  def rowSupplierTaxIdentifierNumber(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(SupplierTaxIdentifierNumberPage).map { num =>
      val url = controllers.routes.SupplierTaxIdentifierNumberController.onPageLoad(CheckMode).url
      (messages("supplierTaxIdentifierNumber.checkYourAnswersLabel"),
       Some(num),
       Seq((url, "site.change", "supplierTaxIdentifierNumber.change.hidden"))
      )
    }

  def rowCurrency(displayName: Option[String])(implicit messages: Messages): Option[Row] =
    displayName.map { name =>
      val url = controllers.routes.RefundingCurrencyController.onPageLoad(CheckMode).url
      (messages("refundingCurrency.checkYourAnswersLabel"),
       Some(name),
       Seq((url, "site.change", "checkYourClaimDetails.refundingCurrency.change.hidden"))
      )
    }

  def rowAmountBeforeVat(answers: UserAnswers, maybeSymbol: Option[String])(implicit messages: Messages): Option[Row] =
    answers.get(TotalPurchaseAmountBeforeVatPage).map { amt =>
      val url = controllers.routes.TotalPurchaseAmountBeforeVatController.onPageLoad(CheckMode).url
      val formattedNumber = f"$amt%,1.2f".replace(".00", "")
      val display = maybeSymbol.map(_ + formattedNumber).getOrElse(formattedNumber)
      (messages("totalPurchaseAmountBeforeVat.checkYourAnswersLabel"),
       Some(display),
       Seq((url, "site.change", "totalPurchaseAmountBeforeVat.change.hidden"))
      )
    }

  def rowVatPaid(answers: UserAnswers, maybeSymbol: Option[String])(implicit messages: Messages): Option[Row] =
    answers.get(pages.TotalVatPaidPage).map { amt =>
      val url = controllers.routes.TotalVatPaidController.onPageLoad(CheckMode).url
      val formattedNumber = f"$amt%,1.2f".replace(".00", "")
      val display = maybeSymbol.map(_ + formattedNumber).getOrElse(formattedNumber)
      (messages("totalVatPaid.checkYourAnswersLabel"), Some(display), Seq((url, "site.change", "totalVatPaid.change.hidden")))
    }

  def rowVatClaim(answers: UserAnswers, maybeSymbol: Option[String])(implicit messages: Messages): Option[Row] =
    answers.get(TotalVatClaimPage).map { amt =>
      val url = controllers.routes.TotalVatClaimController.onPageLoad(CheckMode).url
      val formattedNumber = f"$amt%,1.2f".replace(".00", "")
      val display = maybeSymbol.map(_ + formattedNumber).getOrElse(formattedNumber)
      (messages("totalVatClaim.checkYourAnswersLabel"), Some(display), Seq((url, "site.change", "totalVatClaim.change.hidden")))
    }

  def rowSupplierTaxNumbers(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    // Prefer explicit stored number pages (VAT reg or tax identifier). If
    // neither number is stored but the user explicitly selected "Neither",
    // show the Not provided row. Otherwise return None so the row is hidden.
    answers
      .get(SupplierVatRegistrationNumberPage)
      .map { _num =>
        val url = routes.SupplierTaxNumberController.onPageLoad(CheckMode).url
        // Show the selected type label rather than the raw number so the
        // CYA row reads: "Supplier tax numbers  Supplier VAT registration number"
        (messages("supplierTaxNumber.checkYourAnswersLabel"),
         Some(messages("supplierVatRegistrationNumber.checkYourAnswersLabel")),
         Seq((url, "site.change", "supplierVatRegistrationNumber.change.hidden"))
        )
      }
      .orElse(
        answers.get(SupplierTaxIdentifierNumberPage).map { _num =>
          val url = controllers.routes.SupplierTaxNumberController.onPageLoad(CheckMode).url
          (messages("supplierTaxNumber.checkYourAnswersLabel"),
           Some(messages("supplierTaxIdentifierNumber.checkYourAnswersLabel")),
           Seq((url, "site.change", "supplierTaxIdentifierNumber.change.hidden"))
          )
        }
      )
      .orElse(
        answers.get(SupplierTaxNumberPage) match {
          case Some(models.SupplierTaxNumber.Neither) =>
            Some(
              (messages("supplierTaxNumber.checkYourAnswersLabel"),
               Some(messages("site.notProvided")),
               Seq((routes.SupplierTaxNumberController.onPageLoad(CheckMode).url, "site.change", "supplierTaxNumber.change.hidden"))
              )
            )
          case _ => None
        }
      )

  def sections(answers: UserAnswers,
               maybeCurrencyDisplayName: Option[String],
               maybeCurrencySymbol: Option[String],
               config: ConfigPurchaseMapping,
               showCurrencyRow: Boolean
              )(implicit messages: Messages, request: RequestHeader): Seq[(String, Seq[Row])] = {
    val purchaseCategoryRows =
      Seq(rowPurchaseType(answers), rowPurchaseSubTypeLabel(answers, config), rowPurchaseSubCategoryLabel(answers), rowDescribeItems(answers)).flatten

    val invoiceRows = Seq(rowInvoiceType(answers), rowInvoiceNumber(answers), rowInvoiceDate(answers)).flatten

    val isGermany = answers.get(RefundingCountryPage).contains("DE")

    val supplierRows = (
      Seq(rowSupplierName(answers), rowSupplierAddress(answers)) ++
        (if (isGermany)
           Seq(rowSupplierTaxNumbers(answers), rowSupplierVatRegNumber(answers), rowSupplierTaxIdentifierNumber(answers))
         else Seq(rowSupplierVatRegCheck(answers), rowSupplierVatRegNumber(answers)))
    ).flatten

    // Include an explicit currency selection row when a display name has
    // been provided (e.g. for countries that offer multiple currencies).
    // Display currency symbols on amount rows only when a raw symbol is
    // available (i.e. the user has actually selected a currency).
    val amountsRows = Seq(
      if (showCurrencyRow) rowCurrency(maybeCurrencyDisplayName) else None,
      rowAmountBeforeVat(answers, maybeCurrencySymbol),
      rowVatPaid(answers, maybeCurrencySymbol),
      rowVatClaim(answers, maybeCurrencySymbol)
    ).flatten

    Seq(
      ("purchase.checkYourPurchase.purchaseCategory", purchaseCategoryRows),
      ("purchase.checkYourPurchase.invoiceDetails", invoiceRows),
      ("purchase.checkYourPurchase.supplierDetails", supplierRows),
      ("purchase.checkYourPurchase.purchaseAmounts", amountsRows)
    )
  }

}
