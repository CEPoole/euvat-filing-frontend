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
import forms.DescribeItemsOnInvoiceFormProvider
import models.requests.DataRequest
import models.{CheckMode, Mode, PurchaseType}
import navigation.Navigator
import pages.{DescribeItemsOnInvoicePage, PurchaseSubCategoryPage, PurchaseSubTypePage, PurchaseTypePage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{CheckModeShortCircuit, ConfigPurchaseMapping}
import views.html.DescribeItemsOnInvoiceView

import javax.inject.Inject
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

  // instantiate the form for this request using the provider
  val form = formProvider()

  /** Responsibilities and notes:
    *   - Compute back-targets with `computeBackTarget`, which treats the special-case 'Other' purchase type differently (may route back to sub-type
    *     or purchase type depending on configured options and sentinel values).
    *   - Validate and persist a single `DescribeItemsOnInvoicePage` value.
    *   - In CheckMode unchanged submissions are short-circuited back to the Purchase CYA to avoid extra writes; `utils.CheckModeShortCircuit` is used
    *     to centralise that logic.
    */
  // Compute where the 'back' link should point for this page.
  private def computeBackTarget(mode: Mode)(implicit request: DataRequest[?]): Call =
    // if the journey indicates the parent purchase type is 'other'
    if (isPurchaseTypeOther(request)) determineBackForOther(mode)
    // otherwise delegate to the generic purchase back-link helper
    else PurchaseBackLinkHelper.computeBackTarget(mode)

  // Helper: determine whether the stored PurchaseType is `Other`.
  private def isPurchaseTypeOther(implicit request: DataRequest[?]): Boolean =
    request.userAnswers.get(PurchaseTypePage).contains(PurchaseType.Other)

  // Check if the parent sub-type code ends with sentinel '99' (meaning 'None').
  private def parentIndicatesNone(implicit request: DataRequest[?]): Boolean =
    request.userAnswers.get(PurchaseSubTypePage).exists(v => v.split("\\.").lastOption.contains("99"))

  // Check if the child sub-category code ends with sentinel '99'.
  private def childIndicatesNone(implicit request: DataRequest[?]): Boolean =
    request.userAnswers.get(PurchaseSubCategoryPage).exists(v => v.split("\\.").lastOption.contains("99"))

  // Determine whether the 'other' purchase parent has multiple subcodes.
  private def hasMultipleOtherSubcodes(country: String): Boolean =
    try {
      val opts = configPurchaseMapping.subcodesFor(country, "other")
      // true when there is more than one option for 'other'
      opts.nonEmpty && opts.size > 1
    } catch { case _: Throwable => false }

  // Select the appropriate back target when the overall purchase type is 'other'.
  private def determineBackForOther(mode: Mode)(implicit request: DataRequest[?]): Call =
    // If parent indicates 'none' then we may route to the sub-type selection
    if (parentIndicatesNone) {
      // find the configured country code and choose the correct back target
      utils.CountryCode.findCountryCode(request.userAnswers).fold(controllers.routes.PurchaseTypeController.onPageLoad(mode)) { country =>
        if (hasMultipleOtherSubcodes(country))
          // when multiple 'other' subcodes exist, go back to the PurchaseSubType page
          controllers.purchase.routes.PurchaseSubTypeController.onPageLoad(PurchaseType.slugOf(PurchaseType.Other), mode)
        else
          // otherwise go back to the purchase type selection
          controllers.routes.PurchaseTypeController.onPageLoad(mode)
      }
    } else if (childIndicatesNone)
      // if a child indicates none, go back to the purchase type page
      controllers.routes.PurchaseTypeController.onPageLoad(mode)
    else
      // default back target when 'other' does not have special cases
      PurchaseBackLinkHelper.computeBackTarget(mode)

  // Render the page on GET request.
  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // prepare the form: fill with existing value if present
    val preparedForm = request.userAnswers.get(DescribeItemsOnInvoicePage) match {
      case None        => form
      case Some(value) => form.fill(value)
    }

    // render the view with the prepared form and computed back target
    Ok(view(preparedForm, mode, computeBackTarget(mode)))
  }

  // Handle form submission on POST.
  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // bind the form from the request and handle validation/result
    form
      .bindFromRequest()
      .fold(
        { formWithErrors =>
          if (formWithErrors.errors.exists(_.message == "describeItemsOnInvoice.error.required"))
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(DescribeItemsOnInvoicePage, ""))
              _              <- sessionRepository.set(updatedAnswers)
            } yield Redirect(routes.PurchaseWarningController.onPageLoad(mode))
          else
            // validation errors -> re-render the page with errors and back target
            Future.successful(BadRequest(view(formWithErrors, mode, computeBackTarget(mode))))
        },
        // successful bind -> process the submitted value
        value =>
          if (mode == CheckMode) {
            // In CheckMode: use CheckModeShortCircuit helper to either
            // short-circuit unchanged submissions back to the Purchase CYA
            // or to persist the new value once and then redirect.
            CheckModeShortCircuit(
              DescribeItemsOnInvoicePage,
              value,
              mode,
              request.userAnswers,
              sessionRepository,
              // redirect target when unchanged in CheckMode
              controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad(),
                // onSaved: once persisted, follow the normal navigator for this page (so warnings still display)
                updated => Future.successful(Redirect(navigator.nextPage(DescribeItemsOnInvoicePage, mode, updated)))
            )
          } else {
            // Normal mode: persist and redirect according to the navigator
            CheckModeShortCircuit(
              DescribeItemsOnInvoicePage,
              value,
              mode,
              request.userAnswers,
              sessionRepository,
              // next page determined by navigator for the current answers
              navigator.nextPage(DescribeItemsOnInvoicePage, mode, request.userAnswers),
              // onSaved: redirect to the navigator-determined next page
              updated => Future.successful(Redirect(navigator.nextPage(DescribeItemsOnInvoicePage, mode, updated)))
            )
          }
      )
  }
}
