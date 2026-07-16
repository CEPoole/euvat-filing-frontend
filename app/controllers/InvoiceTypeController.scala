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
import forms.InvoiceTypeFormProvider

import javax.inject.Inject
import models.Mode
import navigation.Navigator
import pages.{InvoiceTypePage, PurchaseTypePage, PurchaseSubTypePage, PurchaseSubCategoryPage, PurchaseSubCategoryLabelPage, PurchaseSubTypeLabelPage, DescribeItemsOnInvoicePage}
import models.requests.DataRequest
import models.PurchaseType
import models.PurchaseSubCategoryType
import controllers.helpers.PurchaseBackLinkHelper
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.InvoiceTypeView
import play.api.mvc.Call

import scala.concurrent.{ExecutionContext, Future}

class InvoiceTypeController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: InvoiceTypeFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: InvoiceTypeView
)(implicit ec: ExecutionContext)
  extends FrontendBaseController
  with I18nSupport
  with play.api.Logging {

  val form = formProvider()

  /**
   * Compute the back target Call without mutating session. Used to render the back link.
   */
  private def computeBackTarget(mode: Mode)(implicit request: DataRequest[?]): play.api.mvc.Call = {
    try {
      logger.info(s"InvoiceTypeController.backLink - purchaseType=${request.userAnswers.get(PurchaseTypePage)}, parent=${request.userAnswers.get(PurchaseSubTypePage)}, child=${request.userAnswers.get(PurchaseSubCategoryPage)}")
    } catch { case _: Throwable => }

    // Only route back to Describe Items when PurchaseType is Other and the
    // chosen sub-category indicates "None of these" (sentinel 99).
    val isOtherWithNoneSelected: Boolean = request.userAnswers.get(PurchaseTypePage).contains(PurchaseType.Other) && {
      val childIsNone = request.userAnswers.get(PurchaseSubCategoryPage).exists(v => v.split("\\.").lastOption.contains("99"))
      val parentIsNone = request.userAnswers.get(PurchaseSubTypePage).exists(v => v.split("\\.").lastOption.contains("99"))
      childIsNone || parentIsNone
    }

    if (isOtherWithNoneSelected) controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(mode)
    else PurchaseBackLinkHelper.computeBackTarget(mode)
  }

  /**
   * Back-link endpoint: when the user clicks the back link this endpoint is hit, clears the
   * appropriate session keys and then redirects to the computed target. This ensures clearing
   * happens at the click moment instead of when InvoiceType is rendered.
   */


  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val preparedForm = request.userAnswers.get(InvoiceTypePage) match {
      case None        => form
      case Some(value) => form.fill(value)
    }

    val back = computeBackTarget(mode)
    Future.successful(Ok(view(preparedForm, mode, back)))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>

    form
      .bindFromRequest()
      .fold(
              formWithErrors =>
                // render errors
                Future.successful(BadRequest(view(formWithErrors, mode, computeBackTarget(mode)))),
        value =>
          for {
            updatedAnswers <- Future.fromTry(request.userAnswers.set(InvoiceTypePage, value))
            _              <- sessionRepository.set(updatedAnswers)
          } yield Redirect(navigator.nextPage(InvoiceTypePage, mode, updatedAnswers))
      )
  }
}
