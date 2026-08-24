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
import forms.SupplierTaxNumberFormProvider
import models.requests.DataRequest
import models.{InvoiceType, Mode, NormalMode, SupplierTaxNumber, UserAnswers}
import navigation.Navigator
import pages.{InvoiceTypePage, SupplierTaxIdentifierNumberPage, SupplierTaxNumberPage, SupplierVatRegistrationNumberPage}
import play.api.Logger
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.*
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.CheckModeShortCircuit
import utils.ControllerHelpers.*
import views.html.SupplierTaxNumberView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SupplierTaxNumberController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: SupplierTaxNumberFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: SupplierTaxNumberView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[SupplierTaxNumber] = formProvider()
  private val logger = Logger(getClass)

  private def backLink: Call = routes.SupplierAddressController.onPageLoad(NormalMode)

  private def requireGermany(userAnswers: UserAnswers): Option[Result] =
    utils.CountryCode.findCountryCode(userAnswers) match {
      case Some("DE") => None
      case _ =>
        logger.warn("SupplierTaxNumberController - country is not Germany or missing from session, redirecting to JourneyRecovery")
        Some(Redirect(routes.JourneyRecoveryController.onPageLoad()))
    }

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Ensure country is Germany and either return recovery redirect or render
    requireGermany(request.userAnswers).getOrElse {
      // Build the prepared form (prefill when a stored answer exists)
      val preparedForm = preparedFormFromAnswers(_.get(SupplierTaxNumberPage), form)
      // Determine whether the invoice type is a simplified invoice
      val isSimplifiedInvoice: Boolean = request.userAnswers.get(InvoiceTypePage).contains(InvoiceType.SimplifiedInvoice)
      // Render the OK view using the shared helper
      okView(preparedForm, mode, isSimplifiedInvoice)
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Ensure Germany precondition; if not satisfied return the redirect
    requireGermany(request.userAnswers) match {
      case Some(result) => Future.successful(result)
      case None         =>
        // Precompute whether the invoice type is simplified for view rendering
        val isSimplifiedInvoice: Boolean = request.userAnswers.get(InvoiceTypePage).contains(InvoiceType.SimplifiedInvoice)

        // Bind and validate the submitted form
        form
          .bindFromRequest()
          .fold(
            // On errors render BadRequest via helper keeping method small
            formWithErrors => Future.successful(badRequestView(formWithErrors, mode, isSimplifiedInvoice)),

            // On valid value handle short-circuiting or persist-and-clean
            value =>
              // Short-circuit unchanged CheckMode submissions to Purchase CYA
              CheckModeShortCircuit.shortCircuitIfUnchanged(
                SupplierTaxNumberPage,
                value,
                mode,
                request.userAnswers,
                controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
              ) match {
                // If short-circuit produced a redirect return it immediately
                case Some(res) => Future.successful(res)
                // Otherwise persist the new choice and clean stale fields once
                case None =>
                  // Build the Try of updated answers (set the selected value)
                  val userAnswersTry = request.userAnswers.set(SupplierTaxNumberPage, value)

                  // Persist and clean stale supplier identifiers according to selection
                  persistWithCleaning(userAnswersTry, value).map { cleaned =>
                    // Redirect to the next page using navigator
                    Redirect(navigator.nextPage(SupplierTaxNumberPage, mode, cleaned))
                  }
              }
          )
    }
  }
  
  // Render OK view with supplied form and simplified-invoice flag
  private def okView(preparedForm: Form[SupplierTaxNumber], mode: Mode, isSimplifiedInvoice: Boolean)(implicit request: DataRequest[?]) =
    Ok(view(preparedForm, mode, backLink, isSimplifiedInvoice))

  // Render BadRequest view with errors and simplified flag
  private def badRequestView(formWithErrors: Form[SupplierTaxNumber], mode: Mode, isSimplifiedInvoice: Boolean)(implicit request: DataRequest[?]) =
    BadRequest(view(formWithErrors, mode, backLink, isSimplifiedInvoice))

  // Persist the provided Try[UserAnswers], then remove stale identifiers based on `value` and save.
  private def persistWithCleaning(userAnswersTry: scala.util.Try[UserAnswers], value: SupplierTaxNumber)(implicit
    request: DataRequest[?]
  ): Future[UserAnswers] =
    Future.fromTry(userAnswersTry).flatMap { updatedAnswers =>
      // Clean stale fields based on chosen supplier tax number type
      val cleaned: UserAnswers = value match {
        case SupplierTaxNumber.Vatregistrationnumber =>
          // If VAT registration selected remove any tax identifier
          updatedAnswers.remove(SupplierTaxIdentifierNumberPage).get
        case SupplierTaxNumber.Taxidentifiernumber =>
          // If tax identifier selected remove any VAT registration number
          updatedAnswers.remove(SupplierVatRegistrationNumberPage).get
        case SupplierTaxNumber.Neither =>
          // If neither selected remove both fields
          updatedAnswers
            .remove(SupplierVatRegistrationNumberPage)
            .get
            .remove(SupplierTaxIdentifierNumberPage)
            .get
      }

      // Persist the cleaned answers once and return them as the future result
      sessionRepository.set(cleaned).map(_ => cleaned)
    }
}
