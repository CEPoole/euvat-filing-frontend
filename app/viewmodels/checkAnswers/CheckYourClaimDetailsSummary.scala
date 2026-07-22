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
import models.{CheckMode, UserAnswers}
import pages.*
import play.api.i18n.{Lang, Messages}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import utils.DateTimeFormats.shortMonthYearFormat
import viewmodels.govuk.summarylist.*
import viewmodels.implicits.*

object CheckYourClaimDetailsSummary {

  type Row = (String, Option[String], Seq[(String, String, String)])

  def rowCountry(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(RefundingCountryNamePage).map { countryName =>
      val changeUrl =
        if (answers.get(ClaimDetailsCompletedPage).contains(true))
          routes.CheckYourStateDetailsController.onPageLoad(CheckMode).url
        else
          routes.RefundingCountryController.onPageLoad(CheckMode).url
      (
        messages("checkYourClaimDetails.refundingCountry.subLabel"),
        Some(countryName),
        Seq((changeUrl, "site.change", "checkYourClaimDetails.refundingCountry.change.hidden"))
      )
    }

  def rowLanguage(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(RefundingLanguagePage).map { answer =>
      (
        messages("checkYourClaimDetails.refundingLanguage.subLabel"),
        Some(messages(s"refundingLanguage.${answer.toString}")),
        Seq((routes.RefundingLanguageController.onPageLoad(CheckMode).url, "site.change", "checkYourClaimDetails.refundingLanguage.change.hidden"))
      )
    }

  def rowCurrency(displayName: Option[String])(implicit messages: Messages): Option[Row] =
    displayName.map { name =>
      (
        messages("checkYourClaimDetails.refundingCurrency.subLabel"),
        Some(name),
        Seq((routes.RefundingCurrencyController.onPageLoad(CheckMode).url, "site.change", "checkYourClaimDetails.refundingCurrency.change.hidden"))
      )
    }

  def rowRefundStart(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(RefundPeriodPage).map { answer =>
      implicit val lang: Lang = messages.lang
      (
        messages("checkYourClaimDetails.refundingPeriodStart.subLabel"),
        Some(answer.startDate.format(shortMonthYearFormat())),
        Seq(
          (routes.RefundPeriodController.onPageLoad(CheckMode).url + "#start.month",
           "site.change",
           "checkYourClaimDetails.refundingStartDate.change.hidden"
          )
        )
      )
    }

  def rowRefundEnd(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(RefundPeriodPage).map { answer =>
      implicit val lang: Lang = messages.lang
      (
        messages("checkYourClaimDetails.refundingPeriodEnd.subLabel"),
        Some(answer.endDate.format(shortMonthYearFormat())),
        Seq(
          (routes.RefundPeriodController.onPageLoad(CheckMode).url + "#end.month",
           "site.change",
           "checkYourClaimDetails.refundingEndDate.change.hidden"
          )
        )
      )
    }

  def rowContactEmail(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(ContactDetailsPage).map { answer =>
      (
        messages("checkYourClaimDetails.contactEmail.subLabel"),
        Some(answer.email),
        Seq((routes.ContactDetailsController.onPageLoad(CheckMode).url + "#contactEmail", "site.change", "checkYourClaimDetails.Email.change.hidden"))
      )
    }

  def rowContactPhone(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(ContactDetailsPage).map { answer =>
      (
        messages("checkYourClaimDetails.contactPhone.subLabel"),
        Some(answer.telephone.getOrElse("Not provided")),
        Seq(
          (routes.ContactDetailsController.onPageLoad(CheckMode).url + "#contactTelephone",
           "site.change",
           "checkYourClaimDetails.Phone.change.hidden"
          )
        )
      )
    }

  def rowBusinessActivity(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(BusinessActivityCodePage).map { answer =>
      val viewUrl = (answers.get(BusinessActivityCodeTwoPage), answers.get(BusinessActivityCodeThreePage)) match {
        case (_, Some(_)) => routes.BusinessActivityThreeController.onPageLoad().url
        case (Some(_), _) => routes.BusinessActivityTwoController.onPageLoad(CheckMode).url
        case _            => routes.BusinessActivityController.onPageLoad(CheckMode).url
      }
      (
        messages("checkYourClaimDetails.businessActivity.subLabel"),
        Some(answer),
        Seq((viewUrl, "site.view", "checkYourClaimDetails.businessActivity1.view.hidden"))
      )
    }

  def rowBusinessActivity2(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(BusinessActivityCodeTwoPage).map { answer =>
      (
        messages("checkYourClaimDetails.businessActivity2.subLabel"),
        Some(answer),
        Seq(
          (routes.BusinessActivityCodeTwoController.onPageLoad(CheckMode).url, "site.change", "checkYourClaimDetails.businessActivity2.change.hidden")
        )
      )
    }

  def rowBusinessActivity3(answers: UserAnswers)(implicit messages: Messages): Option[Row] =
    answers.get(BusinessActivityCodeThreePage).map { answer =>
      (
        messages("checkYourClaimDetails.businessActivity3.subLabel"),
        Some(answer),
        Seq(
          (routes.BusinessActivityCodeThreeController.onPageLoad(CheckMode).url,
           "site.change",
           "checkYourClaimDetails.businessActivity3.change.hidden"
          )
        )
      )
    }
}
