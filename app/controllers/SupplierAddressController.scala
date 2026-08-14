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
import forms.SupplierAddressFormProvider
import models.requests.DataRequest
import models.{CheckMode, Mode, NormalMode, UserAnswers}
import navigation.Navigator
import pages.{PurchaseTypePage, SupplierAddressPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.ControllerHelpers.*
import utils.{CheckModeShortCircuit, SaveAndRedirect}
import views.html.SupplierAddressView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class SupplierAddressController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: SupplierAddressFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: SupplierAddressView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form = formProvider()

  private def backLink: Call = routes.SuppliersNameController.onPageLoad(NormalMode)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Prepare the form by reading any stored SupplierAddress from session
    val preparedForm = preparedFormFromAnswers(_.get(SupplierAddressPage), form)
    // Render page with OK and shared rendering helper
    okView(preparedForm, mode)
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Bind the form and handle both invalid and valid cases
    form
      .bindFromRequest()
      .fold(
        // On form errors render BadRequest using shared helper
        formWithErrors => Future.successful(badRequestView(formWithErrors, mode)),

        // On valid submission, short-circuit in CheckMode or persist once
        value =>
          /*
           * Use the no-persist CheckMode helper so we can compute the
           * correct redirect target before performing a single persist.
           * The continuation composes any additional flags/state and
           * then delegates to SaveAndRedirect to persist once and redirect.
           */
          CheckModeShortCircuit.applyNoPersist(
            SupplierAddressPage,
            value,
            mode,
            request.userAnswers,
            controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad(),
            (answersAfterSet: UserAnswers) => {
              // Wrap the computed answers into a Success so SaveAndRedirect can use it
              val userAnswersTry = Success(answersAfterSet)

              // Compute the redirect target depending on CheckMode and whether
              // we're currently in a purchase flow (PurchaseType present)
              val redirectCall = computeRedirectAfterSave(answersAfterSet, mode)

              // Persist once and redirect using the shared helper
              SaveAndRedirect.saveTryAndRedirect(userAnswersTry, sessionRepository, redirectCall)
            }
          )
      )
  }

  // Render OK view with shared parameters
  private def okView(preparedForm: Form[?], mode: Mode)(implicit request: DataRequest[?]) =
    Ok(view(preparedForm, mode, backLink))

  // Render BadRequest view for forms with errors
  private def badRequestView(formWithErrors: Form[?], mode: Mode)(implicit request: DataRequest[?]) =
    BadRequest(view(formWithErrors, mode, backLink))

  // Compute redirect target after saving SupplierAddress; centralised to avoid duplication
  private def computeRedirectAfterSave(answersAfterSet: UserAnswers, mode: Mode)(implicit request: DataRequest[?]) =
    if (mode == CheckMode && request.userAnswers.get(PurchaseTypePage).isDefined)
      controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
    else
      navigator.nextPage(SupplierAddressPage, mode, answersAfterSet)
}
