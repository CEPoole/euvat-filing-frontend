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
import forms.TotalPurchaseAmountBeforeVatFormProvider
import models.requests.DataRequest
import models.{CheckMode, Mode, SupplierTaxNumber, UserAnswers}
import navigation.Navigator
import pages.*
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.ControllerHelpers.*
import utils.{CheckModeShortCircuit, ConfigCurrencyMapping}
import views.html.TotalPurchaseAmountBeforeVatView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TotalPurchaseAmountBeforeVatController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  configCurrencyMapping: ConfigCurrencyMapping,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: TotalPurchaseAmountBeforeVatFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: TotalPurchaseAmountBeforeVatView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[BigDecimal] = formProvider()
  private def backLink(mode: Mode)(userAnswers: UserAnswers): Call = {
    // If this country requires currency selection, the currency page was shown right before this one.
    userAnswers.get(RefundingCountryPage) match {
      case Some(countryCode) if configCurrencyMapping.requiresCurrencySelection(countryCode) =>
        routes.RefundingCurrencyController.onPageLoad(mode)

      case Some("DE") =>
        // Prefer explicit answers about which supplier tax path was chosen (the
        // SupplierTaxNumberPage), falling back to presence of the specific pages
        // if required.
        userAnswers.get(SupplierVatRegistrationNumberPage) match {
          case Some(_) => routes.SupplierVatRegistrationNumberController.onPageLoad(mode)
          case None =>
            userAnswers.get(SupplierTaxNumberPage) match {
              case Some(SupplierTaxNumber.Vatregistrationnumber) => routes.SupplierVatRegistrationNumberController.onPageLoad(mode)
              case Some(SupplierTaxNumber.Taxidentifiernumber)  => routes.SupplierTaxIdentifierNumberController.onPageLoad(mode)
              case _ =>
                if (userAnswers.get(SupplierTaxIdentifierNumberPage).isDefined) {
                  routes.SupplierTaxIdentifierNumberController.onPageLoad(mode)
                } else {
                  routes.SupplierTaxNumberController.onPageLoad(mode)
                }
            }
        }

      case _ =>
        userAnswers.get(SupplierVatRegistrationNumberPage) match {
          case Some(_) => routes.SupplierVatRegistrationNumberController.onPageLoad(mode)
          case None    => routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(mode)
        }
    }
  }

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Prepare the form value either empty or pre-filled from session
    val preparedForm = preparedFormFromAnswers(_.get(TotalPurchaseAmountBeforeVatPage), form)

    // Resolve display currency name and symbol prefix from session/config
    val (currencyName, prefix) = currencyNameAndPrefix(request.userAnswers, configCurrencyMapping)

    // Render page with prepared form, computed backlink and currency info
    okView(preparedForm, mode, prefix, currencyName)
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        // Invalid form: render the page with validation errors and currency info
        formWithErrors => Future.successful(badRequestView(formWithErrors, mode)),

        value => handleSubmit(value, mode)
      )
  }

  private def handleSubmit(value: BigDecimal, mode: Mode)(implicit request: DataRequest[?]) = {
    shortCircuitPersistAndThen(
      TotalPurchaseAmountBeforeVatPage,
      value,
      mode,
      request.userAnswers,
      sessionRepository,
      navigator.nextPage(TotalPurchaseAmountBeforeVatPage, mode, request.userAnswers),
      controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
    )(updated => Future.successful(Redirect(navigator.nextPage(TotalPurchaseAmountBeforeVatPage, mode, updated))))
  }

  // Render OK view with currency details and computed backlink
  private def okView(preparedForm: Form[BigDecimal], mode: Mode, prefix: String, currencyName: String)(implicit request: DataRequest[?]) =
    Ok(view(preparedForm, mode, backLink(mode)(request.userAnswers), prefix, currencyName))

  // Render BadRequest view for invalid forms, keeping currency resolution consistent
  private def badRequestView(formWithErrors: Form[?], mode: Mode)(implicit request: DataRequest[?]) = {
    val (currencyName, prefix) = currencyNameAndPrefix(request.userAnswers, configCurrencyMapping)
    BadRequest(view(formWithErrors, mode, backLink(mode)(request.userAnswers), prefix, currencyName))
  }
}
