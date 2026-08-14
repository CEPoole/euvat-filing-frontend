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
import forms.SupplierVatRegistrationNumberFormProvider
import models.requests.DataRequest
import models.{CheckMode, Mode}
import navigation.Navigator
import pages.{PurchaseTypePage, RefundingCountryPage, SupplierTaxIdentifierNumberPage, SupplierVatRegistrationNumberPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.CheckModeShortCircuit
import utils.ControllerHelpers.*
import views.html.SupplierVatRegistrationNumberView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class SupplierVatRegistrationNumberController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: SupplierVatRegistrationNumberFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: SupplierVatRegistrationNumberView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[String] = formProvider()

  private def backLink(mode: Mode) = routes.SupplierTaxNumberController.onPageLoad(mode)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Remove any lingering SupplierTaxIdentifierNumber as VAT reg number page takes precedence
    // Execute the cleanup asynchronously but continue to prepare and render the page
    for {
      updatedAnswers <- Future.fromTry(request.userAnswers.remove(SupplierTaxIdentifierNumberPage))
      _              <- sessionRepository.set(updatedAnswers)
    } yield None

    // Prepare the form pre-filling from session when present
    val preparedForm = preparedFormFromAnswers(_.get(SupplierVatRegistrationNumberPage), form)

    // Detect whether refunding country is Germany to inform the view
    val isGermany = request.userAnswers.get(RefundingCountryPage).exists(_.equalsIgnoreCase("DE"))

    // Render the page using the shared helper
    okView(preparedForm, mode, isGermany)
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Determine whether the refunding country is Germany for the form view
    val isGermany = request.userAnswers.get(RefundingCountryPage).exists(_.equalsIgnoreCase("DE"))

    // Bind form and handle validation/result
    form
      .bindFromRequest()
      .fold(
        // On errors render BadRequest via helper
        formWithErrors => Future.successful(badRequestView(formWithErrors, mode, isGermany)),

        // On valid submission attempt CheckMode short-circuiting or persist once
        value =>
          CheckModeShortCircuit.shortCircuitIfUnchanged(
            SupplierVatRegistrationNumberPage,
            value,
            mode,
            request.userAnswers,
            controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
          ) match {
            // If unchanged in CheckMode, short-circuit result present
            case Some(res) => Future.successful(res)
            // Otherwise persist the VAT reg number and redirect appropriately
            case None =>
              val userAnswersTry = request.userAnswers.set(SupplierVatRegistrationNumberPage, value)
              persistAndRedirect(userAnswersTry, mode)
          }
      )
  }

  // Render OK with form and the Germany flag
  private def okView(preparedForm: Form[String], mode: Mode, isGermany: Boolean)(implicit request: DataRequest[?]) =
    Ok(view(preparedForm, mode, backLink(mode), isGermany))

  // Render BadRequest for invalid form submissions
  private def badRequestView(formWithErrors: Form[String], mode: Mode, isGermany: Boolean)(implicit request: DataRequest[?]) =
    BadRequest(view(formWithErrors, mode, backLink(mode), isGermany))

  // Persist once and compute redirect target according to mode and purchase flow
  private def persistAndRedirect(userAnswersTry: Try[models.UserAnswers], mode: Mode)(implicit
    request: DataRequest[?]
  ): Future[play.api.mvc.Result] =
    persistAndThen(userAnswersTry, sessionRepository) { persisted =>
      Future.successful(
        if (mode == CheckMode && request.userAnswers.get(PurchaseTypePage).isDefined)
          Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
        else
          Redirect(navigator.nextPage(SupplierVatRegistrationNumberPage, mode, persisted))
      )
    }

}
