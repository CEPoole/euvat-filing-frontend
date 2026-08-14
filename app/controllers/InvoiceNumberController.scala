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
import forms.InvoiceNumberFormProvider
import models.{CheckMode, Mode, NormalMode, UserAnswers}
import navigation.Navigator
import pages.InvoiceNumberPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.*
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.CheckModeShortCircuit
import views.html.InvoiceNumberView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class InvoiceNumberController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: InvoiceNumberFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: InvoiceNumberView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form = formProvider()

  private def backLink(mode: Mode): Call = routes.InvoiceTypeController.onPageLoad(mode)

  // Render a BadRequest using the InvoiceNumber view, wrapped in a Future
  // so it can be returned from async actions consistently.
  private def badRequestView(formWithErrors: Form[?], mode: Mode)(implicit request: Request[AnyContent]) = {
    // Render the template with explicit implicits then wrap in BadRequest
    val html = view(formWithErrors, mode, routes.InvoiceTypeController.onPageLoad(mode))(request, messagesApi.preferred(request))
    Future.successful(BadRequest(html))
  }

  // Centralise the CheckMode short-circuit / persist logic for invoice numbers.
  // This avoids duplicating the `utils.CheckModeShortCircuit` invocation in the
  // body of `onSubmit` and keeps the public method concise.
  private def handleInvoiceNumberSave(value: String, mode: Mode, userAnswers: UserAnswers)(implicit
    request: Request[AnyContent]
  ): Future[Result] = {
    // Preserve the special SupplierTaxIdentifierWarning flow while
    // adopting the new centralized CheckModeShortCircuit behaviour.
    val wasShown = userAnswers.get(pages.SupplierTaxIdentifierWarningShownPage).contains(true)
    val previousInvoice = userAnswers.get(InvoiceNumberPage)

    if (wasShown) {
      // If the warning was shown previously and the invoice hasn't changed
      // keep showing the warning; otherwise persist the change, clear the
      // flag and route to the supplier tax id page.
      if (previousInvoice.contains(value)) Future.successful(Redirect(routes.SupplierTaxIdentifierWarningController.onPageLoad(mode)))
      else {
        val clearedTry = for {
          setVal <- userAnswers.set(InvoiceNumberPage, value)
          cleared <- setVal.remove(pages.SupplierTaxIdentifierWarningShownPage)
        } yield cleared

        // persist once then redirect to the supplier tax id flow
        Future.fromTry(clearedTry).flatMap { ua =>
          sessionRepository.set(ua).map(_ => Redirect(routes.SupplierTaxIdentifierNumberController.onPageLoad(mode)))
        }
      }
    } else {
      // In CheckMode prefer short-circuit; otherwise persist and follow navigator
      if (mode == CheckMode)
        CheckModeShortCircuit(
          InvoiceNumberPage,
          value,
          mode,
          userAnswers,
          sessionRepository,
          controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad(),
          _ => Future.successful(Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()))
        )
      else
        CheckModeShortCircuit(
          InvoiceNumberPage,
          value,
          mode,
          userAnswers,
          sessionRepository,
          navigator.nextPage(InvoiceNumberPage, mode, userAnswers),
          updated => Future.successful(Redirect(navigator.nextPage(InvoiceNumberPage, mode, updated)))
        )
    }
  }

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Prepare the form, filling with any existing InvoiceNumber in the session
    val preparedForm = request.userAnswers.get(InvoiceNumberPage) match {
      case None        => form // no saved value -> blank form
      case Some(value) => form.fill(value) // existing value -> pre-fill
    }

    // Render the page with the prepared form and a back link to InvoiceType
    Ok(view(preparedForm, mode, routes.InvoiceTypeController.onPageLoad(NormalMode)))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Bind submitted data and either render errors or process the value
    form
      .bindFromRequest()
      .fold(
        // Validation errors -> return a BadRequest rendering the view
        formWithErrors => badRequestView(formWithErrors, mode),

        // Valid input -> handle persistence/short-circuit via helper
        value => handleInvoiceNumberSave(value, mode, request.userAnswers)
      )
  }
}
