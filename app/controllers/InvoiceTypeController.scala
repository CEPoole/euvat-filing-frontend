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
import pages.{DescribeItemsOnInvoicePage, InvoiceTypePage, PurchaseSubCategoryLabelPage, PurchaseSubCategoryPage, PurchaseSubTypeLabelPage, PurchaseSubTypePage, PurchaseTypePage}
import models.requests.DataRequest
import models.PurchaseType
import models.PurchaseSubCategoryType
import controllers.helpers.PurchaseBackLinkHelper
import utils.ConfigPurchaseMapping
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
  configPurchaseMapping: ConfigPurchaseMapping,
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

  /** Compute the back target Call without mutating session. Used to render the back link.
    */
  private def computeBackTarget(mode: Mode)(implicit request: DataRequest[?]): play.api.mvc.Call = {
    try {
      logger.info(
        s"InvoiceTypeController.backLink - purchaseType=${request.userAnswers.get(PurchaseTypePage)}, parent=${request.userAnswers.get(PurchaseSubTypePage)}, child=${request.userAnswers.get(PurchaseSubCategoryPage)}"
      )
    } catch { case _: Throwable => }

    // If PurchaseType is Other and a saved subtype ends with `.99` then
    // consult the configured options for that country. If the country's
    // `other` subcodes contained more than one option, send the user back to
    // the PurchaseSubType page so they can change the choice. Otherwise fall
    // back to the existing DescribeItemsOnInvoice behaviour when appropriate.
    val isOther = request.userAnswers.get(PurchaseTypePage).contains(PurchaseType.Other)

    if (isOther) {
      val parentIsNone = request.userAnswers.get(PurchaseSubTypePage).exists(v => v.split("\\.").lastOption.contains("99"))
      val childIsNone = request.userAnswers.get(PurchaseSubCategoryPage).exists(v => v.split("\\.").lastOption.contains("99"))

      if (parentIsNone) {
        val countryOpt = request.userAnswers
          .get(pages.RefundingCountryPage)
          .orElse(request.userAnswers.get(pages.RefundingCountryNamePage).map(_.split(",").last.trim))

        val multipleOptions = countryOpt
          .flatMap { c =>
            try {
              val opts = configPurchaseMapping.subcodesFor(c, "other")
              if (opts.nonEmpty) Some(opts.size > 1) else None
            } catch { case _: Throwable => None }
          }
          .getOrElse(false)

        // Previously we sent users directly to the PurchaseSubType page when
        // multiple `other` options existed. This caused the DescribeItemsOnInvoice
        // page to be skipped when navigating back from InvoiceType. Prefer
        // returning to DescribeItemsOnInvoice so the user can review or change
        // the free-text details before reselecting sub-type.
        controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(mode)
      } else if (childIsNone) {
        controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(mode)
      } else PurchaseBackLinkHelper.computeBackTarget(mode)
    } else PurchaseBackLinkHelper.computeBackTarget(mode)
  }

  /** Back-link endpoint: when the user clicks the back link this endpoint is hit, clears the appropriate session keys and then redirects to the
    * computed target. This ensures clearing happens at the click moment instead of when InvoiceType is rendered.
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
