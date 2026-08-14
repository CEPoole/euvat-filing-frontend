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
import forms.TotalVatClaimFormProvider

import javax.inject.Inject
import models.{CheckMode, Mode}
import navigation.Navigator
import pages.{RefundingCurrencyPage, TotalVatClaimPage, TotalVatPaidPage}
import play.api.data.Form
import utils.ConfigCurrencyMapping
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import utils.ControllerHelpers.*
import models.requests.DataRequest
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.TotalVatClaimView

import scala.concurrent.{ExecutionContext, Future}

class TotalVatClaimController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: TotalVatClaimFormProvider,
  configCurrencyMapping: ConfigCurrencyMapping,
  val controllerComponents: MessagesControllerComponents,
  view: TotalVatClaimView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[BigDecimal] = formProvider()

  private def backLink(mode: Mode): Call = routes.TotalVatPaidController.onPageLoad(mode)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Prepare the form pre-filling from session when present
    val preparedForm = preparedFormFromAnswers(_.get(TotalVatClaimPage), form)

    // Resolve the display currency symbol (fallback to Euro)
    val currencySymbol = currencySymbolFromSession(request.userAnswers, configCurrencyMapping)

    // Render the OK view using the shared helper
    okView(preparedForm, mode, currencySymbol)
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Bind form and handle invalid/valid branches using shared helpers
    form
      .bindFromRequest()
      .fold(
        // On validation errors render BadRequest with consistent currency symbol
        formWithErrors => Future.successful(badRequestView(formWithErrors, mode)),

        // On valid submission decide short-circuit vs persist-and-redirect
        value =>
          // If this is an in-purchase CheckMode submission prefer to short-circuit when unchanged
          if (mode == CheckMode && request.userAnswers.get(pages.PurchaseTypePage).isDefined) {
            // If stored value equals submitted value, go back to purchase CYA without persisting
            request.userAnswers.get(TotalVatClaimPage) match {
              case Some(prev) if prev == value =>
                Future.successful(Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()))
              case _ =>
                // Otherwise persist once and redirect to purchase CYA
                val userAnswersTry = request.userAnswers.set(TotalVatClaimPage, value)
                persistAndThen(userAnswersTry, sessionRepository)(_ =>
                  Future.successful(Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()))
                )
            }
          } else {
            // Normal flows: persist once then decide whether to show warning or continue
            val userAnswersTry = request.userAnswers.set(TotalVatClaimPage, value)
            persistAndThen(userAnswersTry, sessionRepository) { persistedAnswers =>
              val totalVatPaid = request.userAnswers.get(TotalVatPaidPage).getOrElse(BigDecimal(0))
              if (value > totalVatPaid) Future.successful(Redirect(routes.VatClaimWarningController.onPageLoad(mode)))
              else Future.successful(Redirect(navigator.nextPage(TotalVatClaimPage, mode, persistedAnswers)))
            }
          }
      )
  }

  private def okView(preparedForm: Form[BigDecimal], mode: Mode, currencySymbol: String)(implicit request: DataRequest[?]) =
    Ok(view(preparedForm, mode, backLink(mode), currencySymbol))

  private def badRequestView(formWithErrors: Form[?], mode: Mode)(implicit request: DataRequest[?]) =
    BadRequest(view(formWithErrors, mode, backLink(mode), currencySymbolFromSession(request.userAnswers, configCurrencyMapping)))
}
