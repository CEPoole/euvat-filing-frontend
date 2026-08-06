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
import forms.DescribeItemsOnInvoiceFormProvider
import javax.inject.Inject
import models.Mode
import navigation.Navigator
import pages.{DescribeItemsOnInvoicePage, PurchaseSubCategoryPage, PurchaseSubTypePage, PurchaseTypePage}
import play.api.mvc.Call
import scala.util.Try
import models.requests.DataRequest
import models.PurchaseSubCategoryType
import models.PurchaseType
import controllers.helpers.PurchaseBackLinkHelper
import utils.ConfigPurchaseMapping
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.DescribeItemsOnInvoiceView

import scala.concurrent.{ExecutionContext, Future}

class DescribeItemsOnInvoiceController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  configPurchaseMapping: ConfigPurchaseMapping,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: DescribeItemsOnInvoiceFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: DescribeItemsOnInvoiceView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form = formProvider()

  private def computeBackTarget(mode: Mode)(implicit request: DataRequest[?]): Call =
    // If PurchaseType is Other and the chosen sub-type or sub-category indicates
    // "None of these" (sentinel 99), decide whether to route back to the
    // sub-type selection or the purchase-type selection. If the country's
    // available `other` subcodes contained more than one option then we should
    // return to the sub-type selection so the user can change that choice.
    val isOther = request.userAnswers.get(PurchaseTypePage).contains(models.PurchaseType.Other)

    if (isOther) {
      // Prefer the saved parent (sub-type) when present
      val parentIsNone = request.userAnswers.get(PurchaseSubTypePage).exists(v => v.split("\\.").lastOption.contains("99"))

      if (parentIsNone) {
        // Attempt to resolve the refunding country from stored answers
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

        if (multipleOptions)
          controllers.purchase.routes.PurchaseSubTypeController.onPageLoad(models.PurchaseType.slugOf(models.PurchaseType.Other), mode)
        else controllers.routes.PurchaseTypeController.onPageLoad(mode)
      } else {
        // Fallback: if the child indicates None, route to purchase type as before
        val childIsNone = request.userAnswers.get(PurchaseSubCategoryPage).exists(v => v.split("\\.").lastOption.contains("99"))
        if (childIsNone) controllers.routes.PurchaseTypeController.onPageLoad(mode)
        else PurchaseBackLinkHelper.computeBackTarget(mode)
      }
    } else PurchaseBackLinkHelper.computeBackTarget(mode)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val preparedForm = request.userAnswers.get(DescribeItemsOnInvoicePage) match {
      case None        => form
      case Some(value) => form.fill(value)
    }

    Ok(view(preparedForm, mode, computeBackTarget(mode)))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(view(formWithErrors, mode, computeBackTarget(mode)))),
        value =>
          for {
            updatedAnswers <- Future.fromTry(request.userAnswers.set(DescribeItemsOnInvoicePage, value))
            _              <- sessionRepository.set(updatedAnswers)
          } yield Redirect(navigator.nextPage(DescribeItemsOnInvoicePage, mode, updatedAnswers))
      )
  }
}
