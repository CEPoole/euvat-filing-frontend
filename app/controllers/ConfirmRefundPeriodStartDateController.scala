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
import models.{CheckMode, NormalMode, RefundPeriod}
import pages.RefundPeriodPage

import javax.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.api.Configuration
import queries.TraderKnownFactsQuery
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
  configuration: Configuration,
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

    private def checkEarliestStartDate(
      traderResponse: TraderKnownFactsResponse
      startDate: LocalDateTime,
      endDate: LocalDateTime,
      mode: Mode
    )(using request: DataRequest[?], ec: ExecutionContext): Future[Result] = {
        val startYearMonth = YearMonth.from(startDate)
        val minAllowed     = earliestPermittedStartDate()

        if(startYearMonth.isBefore(minAllowed)) {
          val refundPeriod = RefundPeriod(startDate, endDate)
          for {
            updatedAnswer1 <- Future.fromTry(request.userAnswers.set(TraderKnownFactsQuery, traderResponse))
            updatedAnswer2 <- Future.fromTry(updatedAnswer1.set(RefundPeriodPage, refundPeriod))
                           <- sessionRepository.set(updatedAnswer2)
          } yield Redirect(controllers.routes.ConfirmRefundPeriodStartDateController.onPageLoad(mode))
        } else {
          checkOverlappingPeriod(traderResponse, startDate, endDate, mode)
        }
      }
    }

    def onPageLoad: Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    request.userAnswers.get(RefundPeriodPage) match {
      case None => Redirect(routes.JourneyRecoveryController.onPageLoad())
      case Some(refundPeriod) =>
        val startDate = refundPeriod.startDate.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy"))
        val minDate = computeEarliest(request).map(_.format(DateTimeFormatter.ofPattern("MM/yyyy"))).getOrElse("01/2021")
        val call = routes.RefundPeriodController.onPageLoad(CheckMode)
        Ok(view(startDate, minDate, call))
    }
  }

  def onSubmit(): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    Redirect(routes.ContactDetailsController.onPageLoad(NormalMode))
  }
}
