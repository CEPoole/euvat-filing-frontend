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
import models.{CheckMode, Mode, NormalMode}
import pages.{TotalVatClaimPage, TotalVatPaidPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.VatClaimWarningView

import javax.inject.Inject

class VatClaimWarningController @Inject() (
  override val messagesApi: MessagesApi,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  val controllerComponents: MessagesControllerComponents,
  view: VatClaimWarningView
) extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val totalVatClaiming = request.userAnswers.get(TotalVatClaimPage).getOrElse(BigDecimal(0))
    Ok(view(routes.TotalVatClaimController.onPageLoad(mode), mode, totalVatClaiming))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    mode match {
      case NormalMode => Redirect(routes.JourneyRecoveryController.onPageLoad()) // TODO - redirect to next page
      case CheckMode  => Redirect(routes.JourneyRecoveryController.onPageLoad()) // TODO - redirect to Check your purchase details
    }
  }
}
