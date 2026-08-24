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
import controllers.helpers.PurchaseBackLinkHelper
import forms.InvoiceTypeFormProvider
import models.requests.DataRequest
import models.{CheckMode, InvoiceType, Mode, PurchaseType, UserAnswers}
import navigation.Navigator
import pages.{InvoiceTypePage, PurchaseSubCategoryPage, PurchaseSubTypePage, PurchaseTypePage}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{ConfigPurchaseMapping, CountryCode}
import views.html.InvoiceTypeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** Controller for selecting `InvoiceType`.
  *
  * Notes for maintainers:
  *   - Uses `utils.CheckModeShortCircuit.shortCircuitIfUnchanged` to avoid persisting when a CheckMode submission does not change stored data.
  *   - When the invoice type changes we clear stale supplier-identification answers (VAT reg / tax id) before persisting; callers should rely on
  *     `buildUpdatedTry` to compose the correct `Try[UserAnswers]` and the `persistAndRedirect` helper to persist once and perform post-persist
  *     routing. This keeps a single call to `sessionRepository.set` per action.
  *   - Special-case: when the country is DE there is a different next step (supplier tax number) after persisting.
  */
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
    with Logging {

  val form = formProvider()

  // Helper to render the InvoiceType view with validation errors and wrap
  // the result in a Future so it can be returned from async handlers.
  private def badRequestView(formWithErrors: play.api.data.Form[?], mode: Mode)(implicit request: DataRequest[?]): Future[play.api.mvc.Result] = {
    // Render the view explicitly supplying the current request and messages
    val html = view(formWithErrors, mode, computeBackTarget(mode))(request, messagesApi.preferred(request))
    Future.successful(BadRequest(html))
  }

  /** Compute the back target Call without mutating session. Used to render the back link.
    */
  // Compute the appropriate back link for InvoiceType. When the purchase
  // type is `Other` and either parent/child indicate the 'none' sentinel
  // we prefer returning to DescribeItemsOnInvoice so users can review free
  // text details before changing types. Otherwise use the generic helper.
  private def computeBackTarget(mode: Mode)(implicit request: DataRequest[?]): Call = {
    // Safe logging
    try
      logger.info(
        s"InvoiceTypeController.backLink - purchaseType=${request.userAnswers.get(PurchaseTypePage)}, parent=${request.userAnswers.get(PurchaseSubTypePage)}, child=${request.userAnswers.get(PurchaseSubCategoryPage)}"
      )
    catch { case _: Throwable => }

    // Helper predicate: the parent sub-type indicates the sentinel 'none' value
    def parentIsNone = request.userAnswers.get(PurchaseSubTypePage).exists(v => v.split("\\.").lastOption.contains("99"))

    // Helper predicate: the child sub-category indicates the sentinel 'none' value
    def childIsNone = request.userAnswers.get(PurchaseSubCategoryPage).exists(v => v.split("\\.").lastOption.contains("99"))

    // Helper predicate: the purchase type stored in session is `Other`
    def isOther = request.userAnswers.get(PurchaseTypePage).contains(PurchaseType.Other)

    // If not `Other`, delegate to the generic back-link helper
    if (!isOther) PurchaseBackLinkHelper.computeBackTarget(mode)
    // If `Other` and either parent/child is the 'none' sentinel prefer the
    // free-text DescribeItemsOnInvoice page so users can review details.
    else if (parentIsNone || childIsNone) controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(mode)
    // Fallback to the generic helper
    else PurchaseBackLinkHelper.computeBackTarget(mode)
  }

  /** Back-link endpoint: when the user clicks the back link this endpoint is hit, clears the appropriate session keys and then redirects to the
    * computed target. This ensures clearing happens at the click moment instead of when InvoiceType is rendered.
    */

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Prepare the form value, prefilling when a selection exists in session
    val preparedForm = request.userAnswers.get(InvoiceTypePage) match {
      case None        => form // no stored invoice type
      case Some(value) => form.fill(value) // pre-fill with stored value
    }

    // Compute the back link target without mutating session
    val back = computeBackTarget(mode)

    // Render the page asynchronously
    Future.successful(Ok(view(preparedForm, mode, back)))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Bind submitted form and validate
    form
      .bindFromRequest()
      .fold(
        // Render validation errors using helper to keep code concise
        formWithErrors => badRequestView(formWithErrors, mode),

        // On success, either short-circuit when unchanged in CheckMode or persist
        value => {
          utils.CheckModeShortCircuit.shortCircuitIfUnchanged(
            InvoiceTypePage,
            value,
            mode,
            request.userAnswers,
            controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
          ) match {
            case Some(res) => Future.successful(res)
            case None      => persistAndRedirect(value, mode)
          }
        }
      )
  }

  // Persist changes to InvoiceType and redirect. This helper handles the
  // special-case flag for CheckMode and returns the appropriate next
  // page depending on value and country (DE special-case).
  private def persistAndRedirect(value: InvoiceType, mode: Mode)(implicit request: DataRequest[?]): Future[play.api.mvc.Result] = {
    // Build the Try of updated UserAnswers, persist and then compute the
    // post-persist redirect. When in CheckMode we additionally set a
    // transient `InvoiceTypeChangedPage` flag before persisting so callers
    // can detect a change occurred.
    val userAnswersTry = buildUpdatedTry(value)

    for {
      builtAnswers <- Future.fromTry(userAnswersTry)
      answersWithChangeFlag <-
        if (mode == CheckMode) Future.fromTry(builtAnswers.set(pages.InvoiceTypeChangedPage, true)) else Future.successful(builtAnswers)
      _ <- sessionRepository.set(answersWithChangeFlag)
    } yield postPersistRedirect(mode, value, builtAnswers)
  }

  // Build an updated Try[UserAnswers] for a changed InvoiceType, clearing
  // stale supplier VAT/identifier details when necessary.
  private def buildUpdatedTry(value: InvoiceType)(implicit request: DataRequest[?]): Try[UserAnswers] =
    request.userAnswers.get(InvoiceTypePage) match {
      case Some(prev) if prev != value =>
        for {
          a <- request.userAnswers.remove(pages.SupplierTaxNumberPage)
          b <- a.remove(pages.SimplifiedInvoiceVatRegCheckPage)
          c <- b.remove(pages.SupplierVatRegistrationNumberPage)
          d <- c.set(InvoiceTypePage, value)
        } yield d
      case _ => request.userAnswers.set(InvoiceTypePage, value)
    }

  private def postPersistRedirect(mode: Mode, value: InvoiceType, updatedAnswers: UserAnswers)(implicit request: DataRequest[?]) = {
    // When in CheckMode we prefer to route back into the purchase flow so the
    // user can immediately see the updated value in the CYA. For Germany (DE)
    // there is a specialist next page (SupplierTaxNumber); otherwise decide
    // based on the selected InvoiceType.
    if (mode == CheckMode) {
      val countryOpt = CountryCode.findCountryCode(request.userAnswers)
      countryOpt match {
        case Some("DE") => Redirect(controllers.routes.SupplierTaxNumberController.onPageLoad(CheckMode))
        case _ =>
          value match {
            case InvoiceType.StandardInvoice   => Redirect(controllers.routes.SupplierVatRegistrationNumberController.onPageLoad(CheckMode))
            case InvoiceType.SimplifiedInvoice => Redirect(controllers.routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(CheckMode))
          }
      }
    } else {
      // In NormalMode continue the normal navigation using the navigator
      Redirect(navigator.nextPage(InvoiceTypePage, mode, updatedAnswers))
    }
  }
}
