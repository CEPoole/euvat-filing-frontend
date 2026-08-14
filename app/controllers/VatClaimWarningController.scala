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
import models.{CheckMode, Mode, NormalMode}
import pages.*
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.ConfigCurrencyMapping
import utils.ControllerHelpers.*
import views.html.VatClaimWarningView

import javax.inject.Inject

class VatClaimWarningController @Inject() (
  override val messagesApi: MessagesApi,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  configCurrencyMapping: ConfigCurrencyMapping,
  val controllerComponents: MessagesControllerComponents,
  view: VatClaimWarningView
) extends FrontendBaseController
    with I18nSupport
    with Logging {

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Read the two required values from session: TotalVatPaid and TotalVatClaim
    (request.userAnswers.get(TotalVatPaidPage), request.userAnswers.get(TotalVatClaimPage)) match {
      // Both values present: render the warning view with currency symbol
      case (Some(_), Some(totalVatClaiming)) =>
        // Resolve human-friendly currency symbol (fallback to Euro)
        val currencySymbol = currencySymbolFromSession(request.userAnswers, configCurrencyMapping)
        // Render the warning page, providing the return route and claim amount
        okView(routes.TotalVatClaimController.onPageLoad(mode), mode, currencySymbol, totalVatClaiming)

      // Missing required session data: log and recover the journey
      case _ =>
        logger.warn("Missing session data")
        Redirect(routes.JourneyRecoveryController.onPageLoad())
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Handle post from the warning page; behavior currently stubbed (TODOs)
    mode match {
      // NormalMode: currently redirect to journey recovery as a placeholder
      case NormalMode => Redirect(routes.JourneyRecoveryController.onPageLoad())

      // CheckMode: currently redirect to journey recovery (should route to CYA)
      case CheckMode => Redirect(routes.JourneyRecoveryController.onPageLoad())
    }
  }

  // Render the OK view for the VatClaimWarning page with given return route
  private def okView(returnCall: Call, mode: Mode, currencySymbol: String, totalVatClaiming: BigDecimal)(implicit
    request: DataRequest[?]
  ) =
    Ok(view(returnCall, mode, currencySymbol, totalVatClaiming))
}
