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
import forms.SimplifiedInvoiceVatRegCheckFormProvider
import models.requests.DataRequest
import models.{CheckMode, Mode, NormalMode}
import navigation.Navigator
import pages.{PurchaseTypePage, SimplifiedInvoiceVatRegCheckPage, SupplierAddressPage, SupplierVatRegistrationNumberPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{CheckModeShortCircuit, ControllerHelpers}
import views.html.SimplifiedInvoiceVatRegCheckView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SimplifiedInvoiceVatRegCheckController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: SimplifiedInvoiceVatRegCheckFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: SimplifiedInvoiceVatRegCheckView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form = formProvider()
  private def backLink: Call = routes.SupplierAddressController.onPageLoad(NormalMode)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Ensure a supplier address exists in session; otherwise recover journey
    request.userAnswers.get(SupplierAddressPage) match {
      // If there's no supplier address we cannot continue; send user to recovery
      case None => Redirect(routes.JourneyRecoveryController.onPageLoad())
      // When supplier address exists render the page (possibly pre-filled)
      case Some(_) =>
        // Prepare the form value by reading the stored answer (if any)
        val preparedForm = ControllerHelpers.preparedFormFromAnswers(_.get(SimplifiedInvoiceVatRegCheckPage), form)
        // Render the page with an OK result using the shared helper
        okView(preparedForm, mode)
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Bind the submitted form from the request and handle both branches
    form
      .bindFromRequest()
      .fold(
        // Invalid form: render the page with validation errors using helper
        formWithErrors => Future.successful(badRequestView(formWithErrors, mode)),

        // Valid form submission: possibly short-circuit or persist once
        value =>
          // Use short-circuiting to avoid persisting when nothing changed
          CheckModeShortCircuit.shortCircuitIfUnchanged(
            SimplifiedInvoiceVatRegCheckPage,
            value,
            mode,
            request.userAnswers,
            controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
          ) match {
            // If short-circuit produced a redirect result return it immediately
            case Some(res) => Future.successful(res)
            // Otherwise build the single Try[UserAnswers] and persist once
            case None =>
              // Compose the updated UserAnswers as a Try (do not persist yet)
              val userAnswersTry = request.userAnswers.set(SimplifiedInvoiceVatRegCheckPage, value)

              // Branch routing based on mode and whether we're in a purchase flow
              (mode, request.userAnswers.get(PurchaseTypePage)) match {
                // In a CheckMode purchase flow where answer is false: clear supplier VAT and go back to CYA
                case (CheckMode, Some(_)) if !value =>
                  for {
                    afterSet     <- Future.fromTry(userAnswersTry)
                    afterCleared <- Future.fromTry(afterSet.remove(SupplierVatRegistrationNumberPage))
                    _            <- sessionRepository.set(afterCleared)
                  } yield Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())

                // In a CheckMode purchase flow where answer is true: persist and route to VAT number page
                case (CheckMode, Some(_)) if value =>
                  for {
                    afterSet <- Future.fromTry(userAnswersTry)
                    _        <- sessionRepository.set(afterSet)
                  } yield Redirect(routes.SupplierVatRegistrationNumberController.onPageLoad(mode))

                // Default: persist and continue normal navigation for non-check or non-purchase flows
                case _ =>
                  for {
                    persistedAnswers <- Future.fromTry(userAnswersTry)
                    _                <- sessionRepository.set(persistedAnswers)
                  } yield {
                    if (value) Redirect(routes.SupplierVatRegistrationNumberController.onPageLoad(mode))
                    else Redirect(routes.TotalPurchaseAmountBeforeVatController.onPageLoad(mode))
                  }
              }
          }
      )
  }

  // Render the page with an OK status using the shared view rendering helper
  private def okView(formToRender: Form[?], mode: Mode)(implicit request: DataRequest[?]) =
    Ok(view(formToRender, mode, backLink))

  // Render the page with a BadRequest status (used for form errors)
  private def badRequestView(formWithErrors: Form[?], mode: Mode)(implicit request: DataRequest[?]) =
    BadRequest(view(formWithErrors, mode, backLink))
}
