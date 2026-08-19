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

import javax.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.SupplierTaxIdentifierWarningView

class SupplierTaxIdentifierWarningController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: repositories.SessionRepository,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  val controllerComponents: MessagesControllerComponents,
  view: SupplierTaxIdentifierWarningView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // mark that the warning was shown and persist, then render the view
    val flagged = request.userAnswers.set(pages.SupplierTaxIdentifierWarningShownPage, true)
    Future
      .fromTry(flagged)
      .flatMap(ua =>
        sessionRepository
          .set(ua)
          .map(_ =>
            Ok(
              view(
                controllers.routes.SupplierTaxIdentifierNumberController.onPageLoad(mode),
                controllers.routes.InvoiceNumberController.onPageLoad(mode),
                controllers.routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode),
                mode
              )
            )
          )
      )
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // clear the warning flag and continue
    val cleared = request.userAnswers.remove(pages.SupplierTaxIdentifierWarningShownPage)
    Future
      .fromTry(cleared)
      .flatMap(ua => sessionRepository.set(ua).map(_ =>
      mode match {
        case CheckMode => Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
        case _         => Redirect(controllers.routes.TotalPurchaseAmountBeforeVatController.onPageLoad(mode))
      }
    ))
  }

}
