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

import controllers.actions.{DataRequiredAction, DataRetrievalAction, IdentifierAction}
import forms.InvoiceDateFormProvider
import models.Mode
import models.requests.DataRequest
import navigation.Navigator
import pages.{InvoiceDatePage, PurchaseTypePage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.*
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.InvoiceDateView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class InvoiceDateController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: InvoiceDateFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: InvoiceDateView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  private def form(implicit messages: Messages) = formProvider()

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Ensure the refund period exists in the session; otherwise recover
    request.userAnswers.get(pages.RefundPeriodPage) match {
      case None =>
        // Missing refund period -> redirect to journey recovery
        Redirect(routes.JourneyRecoveryController.onPageLoad())

      case Some(_) =>
        // Prepare the form by filling with any existing InvoiceDate value
        val preparedForm = request.userAnswers.get(InvoiceDatePage) match {
          case None        => form // no cached date, use empty form
          case Some(value) => form.fill(value) // pre-fill with existing date
        }

        // Render the page pointing at the next step (InvoiceNumber)
        Ok(view(preparedForm, mode, routes.InvoiceNumberController.onPageLoad(mode)))
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Bind the submitted form and validate
    form
      .bindFromRequest()
      .fold(
        // Validation errors -> render BadRequest pointing to InvoiceNumber
        formWithErrors => badRequestToInvoiceNumber(formWithErrors, mode),

        // Valid submission -> perform additional validation and then handle
        value =>
          // Ensure refund period is still present in session
          request.userAnswers.get(pages.RefundPeriodPage) match {
            case None => Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))

            case Some(refundPeriod) =>
              // Prevent future dates: compute today's date and compare
              val today = java.time.LocalDate.now()
              if (value.isAfter(today)) {
                // Rebind to attach an error message for future dates
                val errorForm = form.bindFromRequest().withError("value", "invoiceDate.error.past")
                badRequestToInvoiceNumber(errorForm, mode)
              }

              /*
            TODO: business rule to prevent users entering a date outside the refund period
            this is currently commented out as
            it is not yet clear whether this validation will be possible at this point in the journey.
            Once the refund period can be calculated, this validation should be added back in
            and the relevant error message added to the messages file.

            else if (value.isBefore(refundPeriod.startDate) || value.isAfter(refundPeriod.endDate)) {
              val errorForm = form.withError("value", "invoiceDate.error.outsideRefundPeriod")
              Future.successful(BadRequest(view(errorForm, mode, routes.InvoiceNumberController.onPageLoad(mode))))
            } */
              else {
                // Delegate to `handleSubmission` which contains CheckMode-aware
                // short-circuit logic for purchase journeys and the normal
                // persist-and-redirect path for other flows.
                handleSubmission(value, mode)(request)
              }
          }
      )
  }

  // Small helper to centralise creating a BadRequest that renders the
  // InvoiceNumber flow target with the provided form errors.
  private def badRequestToInvoiceNumber(formWithErrors: Form[?], mode: Mode)(implicit
    request: Request[AnyContent]
  ): Future[play.api.mvc.Result] = {
    // Render template with the required implicits and then wrap in BadRequest
    val html = view(formWithErrors, mode, routes.InvoiceNumberController.onPageLoad(mode))(request, messagesApi.preferred(request))
    Future.successful(BadRequest(html))
  }

  // Persist the invoice date and redirect to the navigator's next page.
  private def persistAndRedirectToNext(value: LocalDate, mode: Mode)(implicit request: DataRequest[?]): Future[Result] =
    for {
      persistedAnswers <- Future.fromTry(request.userAnswers.set(InvoiceDatePage, value))
      _                <- sessionRepository.set(persistedAnswers)
    } yield Redirect(navigator.nextPage(InvoiceDatePage, mode, persistedAnswers))

  // Handle InvoiceDate submission with CheckMode-aware short-circuiting.
  // When inside a purchase journey and the value is unchanged in CheckMode
  // the request is returned directly to Purchase CYA without persisting.
  private def handleSubmission(value: LocalDate, mode: Mode)(implicit request: DataRequest[?]): Future[Result] = {
    if (mode == models.CheckMode && request.userAnswers.get(PurchaseTypePage).isDefined) {
      request.userAnswers.get(InvoiceDatePage) match {
        case Some(prev) if prev == value =>
          // Unchanged in CheckMode; avoid persisting and return to CYA.
          Future.successful(Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()))

        case _ =>
          utils.CheckModeShortCircuit.shortCircuitIfUnchanged(
            InvoiceDatePage,
            value,
            mode,
            request.userAnswers,
            controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
          ) match {
            case Some(res) => Future.successful(res)
            case None =>
              for {
                persistedAnswers <- Future.fromTry(request.userAnswers.set(InvoiceDatePage, value))
                _                <- sessionRepository.set(persistedAnswers)
              } yield Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
          }
      }
    } else {
      persistAndRedirectToNext(value, mode)
    }
  }
}
