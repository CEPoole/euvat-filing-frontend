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

import controllers.actions.*
import models.requests.DataRequest
import models.{CheckMode, Mode, NormalMode, RefundPeriod}
import pages.RefundPeriodPage

import javax.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.ConfirmRefundPeriodStartDateView

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, MonthDay, YearMonth}

class ConfirmRefundPeriodStartDateController @Inject() (
  override val messagesApi: MessagesApi,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  val controllerComponents: MessagesControllerComponents,
  view: ConfirmRefundPeriodStartDateView
) extends FrontendBaseController
    with I18nSupport {

  private def earliestPermittedStartDate(today: LocalDate = LocalDate.now()): YearMonth = {
    val cutoff = MonthDay.of(9, 30).atYear(today.getYear)
    if (!today.isAfter(cutoff)) {
      YearMonth.of(today.getYear - 1, 1)
    } else {
      YearMonth.of(today.getYear, 1)
    }
  }

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    request.userAnswers.get(RefundPeriodPage) match {
      case None => Redirect(routes.JourneyRecoveryController.onPageLoad())
      case Some(refundPeriod) =>
        val startDate = refundPeriod.startDate.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy"))
        val minDate = earliestPermittedStartDate().format(DateTimeFormatter.ofPattern("MM/yyyy"))
        val call = routes.RefundPeriodController.onPageLoad(CheckMode)
        Ok(view(startDate, minDate, call, mode))
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    mode match {
      case NormalMode => Redirect(routes.ContactDetailsController.onPageLoad(NormalMode))
      case CheckMode  => Redirect(routes.CheckYourClaimDetailsController.onPageLoad())
    }
  }
}
