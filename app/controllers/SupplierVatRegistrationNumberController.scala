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
import models.requests.{DataRequest, SupplierVrnCountRequest}
import models.{Mode, CheckMode, UserAnswers, InvoiceType}
import navigation.Navigator
import pages.*
import play.api.data.Form
import play.api.i18n.Lang.logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.*
import repositories.SessionRepository
import services.EuVatRefundsService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
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
  euVatRefundsService: EuVatRefundsService,
  view: SupplierVatRegistrationNumberView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[String] = formProvider()

  private def backLink(mode: Mode)(implicit request: DataRequest[?]): Call = {
    val warningActive = request.userAnswers.get(VrnWarningFlowPage).isDefined
    val isGermany     = request.userAnswers.get(RefundingCountryPage).contains("DE")
    val isSimplified  = request.userAnswers.get(InvoiceTypePage).contains(InvoiceType.SimplifiedInvoice)

    (warningActive, isGermany, isSimplified) match {
      case (true, _, _)     => routes.InvoiceNumberController.onPageLoad(mode)
      case (_, true, _)     => routes.SupplierTaxNumberController.onPageLoad(mode)
      case (_, false, true) => routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(mode)
      case _                => routes.SupplierAddressController.onPageLoad(mode)
    }
  }

  // TODO: Add a supplier-VAT-registration-number warning flow analogous to
  // `SupplierTaxIdentifierWarningController`. When the user arrives from the
  // Invoice number page in `CheckMode` and updates the VAT registration number we
  // should short-circuit into that warning flow. The warning controller and
  // related session flag are not yet implemented for VAT registration numbers.

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
    val isGermany = request.userAnswers.get(RefundingCountryPage).exists(_.equalsIgnoreCase("DE"))

    form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(badRequestView(formWithErrors, mode, isGermany)),

        value => {
          val changed = !request.userAnswers.get(SupplierVatRegistrationNumberPage).contains(value)
          val cameFromInvoicePage = request.userAnswers.get(pages.SupplierVatRegistrationArrivedFromInvoicePage).contains(true)

          // CheckMode short-circuit: unchanged and not arrived from invoice → straight back to CYA
          if (mode == CheckMode && !changed && !cameFromInvoicePage)
            Future.successful(Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()))
          else {
            for {
              updated <- Future.fromTry(request.userAnswers.set(SupplierVatRegistrationNumberPage, value))
              // 7224: clear the VRN warning marker when the VRN genuinely changes
              withFlag <- Future.fromTry(
                              if (request.userAnswers.get(VrnWarningFlowPage).isDefined && changed)
                                updated.set(VrnWarningFlowPage, false)
                              else
                                scala.util.Success(updated)
                            )
            // his refactor: drop the transient arrived-from-invoice flag before persisting
              finalAnswers <- Future.fromTry(withFlag.remove(pages.SupplierVatRegistrationArrivedFromInvoicePage))
              _            <- sessionRepository.set(finalAnswers)
              result       <- checkDuplicate(value, finalAnswers, mode)
            } yield result
          }
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

  private def checkDuplicate(vatNumber: String, answers: UserAnswers, mode: Mode)(implicit request: DataRequest[?]): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    val maybeRequest = for {
      applicationId <- answers.get(ClaimApplicationResponsePage).map(_.applicationId)
      itemNumber    <- answers.get(AddPurchaseResponsePage).map(_.itemNumber)
      invoiceNumber <- answers.get(InvoiceNumberPage)
    } yield SupplierVrnCountRequest(applicationId.toLong, itemNumber, vatNumber, invoiceNumber)

    maybeRequest match {
      case Some(req) =>
        euVatRefundsService
          .getSupplierVrnCount(req)
          .flatMap { response =>
            if (response.duplicateCount > 0)
              Future.successful(Redirect(routes.SupplierVrnWarningController.onPageLoad(mode)))
            else
              for {
                cleared <- Future.fromTry(answers.remove(VrnWarningFlowPage))
                _       <- sessionRepository.set(cleared)
              } yield Redirect(navigator.nextPage(SupplierVatRegistrationNumberPage, mode, cleared))
          }
          .recover { case ex: Exception =>
            logger.error("Error retrieving supplier VRN count", ex)
            Redirect(routes.JourneyRecoveryController.onPageLoad())
          }
      case None =>
        logger.warn("Missing data for duplicate VRN check")
        Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
    }
  }
}
