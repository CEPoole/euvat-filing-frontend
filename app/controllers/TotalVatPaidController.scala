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
import forms.TotalVatPaidFormProvider
import models.requests.DataRequest
import models.{CheckMode, Mode}
import navigation.Navigator
import pages.{PurchaseTypePage, TotalPurchaseAmountBeforeVatPage, TotalVatPaidPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.ConfigCurrencyMapping
import utils.ControllerHelpers.*
import views.html.TotalVatPaidView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/** Handles the total VAT paid input.
  *
  * Behaviour notes:
  *   - Uses `utils.CurrencyResolver.currencyNameAndPrefix` to render currency prefixes consistently across views.
  *   - In purchase journeys running in `CheckMode` we short-circuit unchanged submissions back to the Purchase CYA without persisting to maintain a
  *     single-write invariant. If the value changes we persist once and then redirect appropriately.
  */
class TotalVatPaidController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  configCurrencyMapping: ConfigCurrencyMapping,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: TotalVatPaidFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: TotalVatPaidView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[BigDecimal] = formProvider()

  private def backLink(mode: Mode) = routes.TotalPurchaseAmountBeforeVatController.onPageLoad(mode)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Prepare the form by reading any stored TotalVatPaid from session
    val preparedForm = preparedFormFromAnswers(_.get(TotalVatPaidPage), form)

    // Resolve the currency display name and prefix for the view
    val (currencyName, prefix) = currencyNameAndPrefix(request.userAnswers, configCurrencyMapping)

    // Render OK view using shared helper
    okView(preparedForm, mode, prefix, currencyName)
  }
  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Bind the form and handle errors/valid submission using helpers
    form
      .bindFromRequest()
      .fold(
        // Invalid form: render BadRequest with resolved currency info
        formWithErrors => Future.successful(badRequestView(formWithErrors, mode)),

        // Valid submission: short-circuit unchanged CheckMode or persist once
        value =>
          if (mode == CheckMode && request.userAnswers.get(PurchaseTypePage).isDefined) {
            // When inside a purchase flow and in CheckMode, avoid persisting if unchanged
            request.userAnswers.get(TotalVatPaidPage) match {
              case Some(prev) if prev == value =>
                Future.successful(Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()))
              case _ =>
                // Persist once then redirect to purchase CYA
                val userAnswersTry = request.userAnswers.set(TotalVatPaidPage, value)
                persistAndThen(userAnswersTry, sessionRepository)(_ =>
                  Future.successful(Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()))
                )
            }
          } else {
            // Normal flows: persist once then validate against total purchase amount
            val userAnswersTry = request.userAnswers.set(TotalVatPaidPage, value)
            persistAndThen(userAnswersTry, sessionRepository) { persistedAnswers =>
              val totalPurchaseAmt: BigDecimal = request.userAnswers.get(TotalPurchaseAmountBeforeVatPage).getOrElse(BigDecimal(0))
              if (value >= totalPurchaseAmt) Future.successful(Redirect(routes.VatPaidWarningController.onPageLoad(mode)))
              else Future.successful(Redirect(navigator.nextPage(TotalVatPaidPage, mode, persistedAnswers)))
            }
          }
      )
  }

  // Render OK view with prepared form and currency details
  private def okView(preparedForm: Form[BigDecimal], mode: Mode, prefix: String, currencyName: String)(implicit request: DataRequest[?]) =
    Ok(view(preparedForm, mode, backLink(mode), prefix, currencyName))

  // Render BadRequest view for invalid forms with consistent currency info
  private def badRequestView(formWithErrors: Form[?], mode: Mode)(implicit request: DataRequest[?]) = {
    val (currencyName, prefix) = currencyNameAndPrefix(request.userAnswers, configCurrencyMapping)
    BadRequest(view(formWithErrors, mode, backLink(mode), prefix, currencyName))
  }
}
