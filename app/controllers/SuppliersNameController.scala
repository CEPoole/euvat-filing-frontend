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
import forms.SuppliersNameFormProvider
import models.{CheckMode, Mode}
import models.requests.DataRequest
import navigation.Navigator
import pages.SuppliersNamePage
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.CheckModeShortCircuit
import utils.ControllerHelpers.*
import views.html.SuppliersNameView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SuppliersNameController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: SuppliersNameFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: SuppliersNameView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[String] = formProvider()

  /** Supplier name page.
    *
    * Behaviour notes:
    *   - Pre-fills form from session when present.
    *   - In CheckMode inside a purchase flow, unchanged submissions are short-circuited back to the Purchase CYA to prevent extra writes.
    *   - When a change occurs the new value is persisted once and the user is redirected appropriately (CYA in CheckMode or navigator in NormalMode).
    */

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Prepare the form value by reading the stored SuppliersName if present
    val preparedForm = preparedFormFromAnswers(_.get(SuppliersNamePage), form)
    // Render the page with OK and the invoice date as the back link
    renderOk(preparedForm, mode)
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Bind the incoming form data and branch on validation result
    form
      .bindFromRequest()
      .fold(
        // Invalid form: render BadRequest using shared helper
        formWithErrors => Future.successful(renderBadRequest(formWithErrors, mode)),

        // Valid form: handle CheckMode short-circuiting and persistence
        value =>
          // Determine if we're inside a purchase flow by presence of PurchaseType
          val inPurchaseFlow = request.userAnswers.get(pages.PurchaseTypePage).isDefined

          // If in purchase flow, attempt short-circuit to avoid extra writes
          if (inPurchaseFlow) {
            CheckModeShortCircuit.shortCircuitIfUnchanged(
              pages.SuppliersNamePage,
              value,
              mode,
              request.userAnswers,
              controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
            ) match {
              // Short-circuit produced a redirect; return it
              case Some(res) => Future.successful(res)
              // Otherwise persist once and redirect based on mode
              case None =>
                val userAnswersTry = request.userAnswers.set(SuppliersNamePage, value)
                if (mode == CheckMode)
                  persistAndThen(userAnswersTry, sessionRepository)(_ =>
                    Future.successful(Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()))
                  )
                else
                  persistAndThen(userAnswersTry, sessionRepository)(ua =>
                    Future.successful(Redirect(navigator.nextPage(SuppliersNamePage, mode, ua)))
                  )
            }
          } else {
            // Not in purchase flow: persist and continue normal navigation
            val userAnswersTry = request.userAnswers.set(SuppliersNamePage, value)
            persistAndThen(userAnswersTry, sessionRepository)(ua => Future.successful(Redirect(navigator.nextPage(SuppliersNamePage, mode, ua))))
          }
      )
  }

  // Render OK view with supplied form and mode
  private def renderOk(preparedForm: Form[String], mode: Mode)(implicit request: DataRequest[?]) = Ok(
    view(preparedForm, mode, routes.InvoiceDateController.onPageLoad(mode))
  )

  // Render BadRequest view for invalid forms
  private def renderBadRequest(formWithErrors: Form[String], mode: Mode)(implicit request: DataRequest[?]) = BadRequest(
    view(formWithErrors, mode, routes.InvoiceDateController.onPageLoad(mode))
  )
}
