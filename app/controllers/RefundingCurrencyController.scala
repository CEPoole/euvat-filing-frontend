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
import forms.RefundingCurrencyFormProvider
import models.requests.DataRequest
import models.{Mode, RefundingCurrency, UserAnswers}
import navigation.Navigator
import pages.{ClaimDetailsAmendedPage, ClaimDetailsCompletedPage, CurrencyChangedPage, RefundingCurrencyPage}
import play.api.Logger
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.*
import repositories.SessionRepository
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.*
import views.html.RefundingCurrencyView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class RefundingCurrencyController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: RefundingCurrencyFormProvider,
  configCurrencyMapping: ConfigCurrencyMapping,
  configLanguageMapping: ConfigLanguageMapping,
  val controllerComponents: MessagesControllerComponents,
  view: RefundingCurrencyView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[RefundingCurrency] = formProvider()
  private val logger = Logger(getClass)

  private def backLink(mode: Mode): Call = routes.SupplierVatRegistrationNumberController.onPageLoad(mode)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    CountryCode.findCountryCode(request.userAnswers) match {
      case None =>
        logger.warn("RefundingCurrencyController.onPageLoad - no refunding country in session, redirecting to JourneyRecovery")
        Redirect(routes.JourneyRecoveryController.onPageLoad())
      case Some(countryCode) =>
        val currencies = configCurrencyMapping.currenciesFor(countryCode)
        val msgs = messagesApi.preferred(request)
        val items = buildRadioItems(currencies, msgs)
        val preparedForm = request.userAnswers
          .get(RefundingCurrencyPage)
          .flatMap { storedCode =>
            currencies.find(_._2 == storedCode).map { case (name, _, _) =>
              form.fill(RefundingCurrency.values.find(_.toString == name).getOrElse(RefundingCurrency.Euro))
            }
          }
          .getOrElse(form)

        // Render the page with the prepared form and radio items
        Ok(view(preparedForm, items, backLink(mode), mode))
    }
  }

  // Bind and process the submitted ref currency form. Delegates to
  // small helpers for error & success branches to keep this method
  // concise and under ~50 lines.
  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    form.bindFromRequest().fold(handleFormErrors(_, mode), handleValidSubmission(_, mode))
  }

  // Handle the form-with-errors branch: render the same page with the
  // validation errors shown. If we cannot determine the refunding
  // country from session, recover the journey.
  private def handleFormErrors(formWithErrors: Form[?], mode: Mode)(implicit request: DataRequest[?]): Future[Result] =
    // Use DataRequest to access `userAnswers` stored on the request
    utils.CountryCode.findCountryCode(request.userAnswers) match {
      case None =>
        // Missing country while rendering form errors is unexpected; log
        // and redirect to journey recovery.
        logger.warn(
          "RefundingCurrencyController.onSubmit - no refunding country in session while binding form errors; redirecting to JourneyRecovery"
        )
        Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
      case Some(countryCode) =>
        // Build the radio items again for the page rendering
        val currencies = configCurrencyMapping.currenciesFor(countryCode)
        val msgs = messagesApi.preferred(request)
        val items = buildRadioItems(currencies, msgs)
        Future.successful(BadRequest(view(formWithErrors, items, backLink(mode), mode)))
    }

  // Handle the successful form submission branch. Resolve the currency
  // code and compose any dependent flags, then persist once via
  // `SaveAndRedirect` to centralise single-write semantics.
  private def handleValidSubmission(value: RefundingCurrency, mode: Mode)(implicit request: DataRequest[?]): Future[Result] =
    // Use DataRequest to access session `userAnswers` and other request data
    CountryCode.findCountryCode(request.userAnswers) match {
      case None =>
        // Missing country -> cannot proceed
        logger.warn("RefundingCurrencyController.onSubmit - no refunding country in session; redirecting to JourneyRecovery")
        Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
      case Some(countryCode) =>
        // Lookup configured currencies for the resolved country
        val currencies = configCurrencyMapping.currenciesFor(countryCode)
        // Resolve the chosen currency code by matching the radio value
        currencies.find(_._1.equalsIgnoreCase(value.toString)).map(_._2) match {
          case None =>
            // Unexpected: selected currency not found in configuration
            logger.warn(s"RefundingCurrencyController.onSubmit - could not find currency code for ${value.toString}")
            Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
          case Some(currencyCode) =>
            // Determine whether the currency actually changed compared to session
            val isChanged = request.userAnswers.get(RefundingCurrencyPage) match {
              case Some(existing) => existing != currencyCode
              case None           => true
            }

            // Prepare the purchase CYA target used for CheckMode continuations
            val purchaseCYA = controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()

            // We use the non-persisting CheckMode variant so we can compose
            // additional flags (`ClaimDetailsAmendedPage`, `CurrencyChangedPage`)
            // into a single Try before persisting once.
            CheckModeShortCircuit.applyNoPersist(
              RefundingCurrencyPage,
              currencyCode,
              mode,
              request.userAnswers,
              purchaseCYA,
              (answersAfterSet: UserAnswers) => {
                // If the claim was previously marked completed and the
                // currency changed, set the amended flag so downstream
                // flows surface correct messaging.
                val maybeAmendedTry =
                  if (isChanged && request.userAnswers.get(ClaimDetailsCompletedPage).contains(true))
                    answersAfterSet.set(ClaimDetailsAmendedPage, true)
                  else Success(answersAfterSet)

                // If the currency changed mark a dedicated flag so other
                // pages can react to the change. Chain after amended flag.
                val maybeCurrencyChangedTry = maybeAmendedTry.flatMap { ua =>
                  if (isChanged) ua.set(CurrencyChangedPage, true) else Success(ua)
                }

                maybeCurrencyChangedTry match {
                  case Success(finalAnswers) =>
                    // Persist once and redirect: in CheckMode to the
                    // Purchase CYA, otherwise follow the navigator.
                    SaveAndRedirect.saveTryAndRedirect(
                      Success(finalAnswers),
                      sessionRepository,
                      if (mode == models.CheckMode) purchaseCYA else navigator.nextPage(RefundingCurrencyPage, mode, finalAnswers)
                    )
                  case Failure(_) =>
                    // Failure while composing UserAnswers -> 500
                    Future.successful(InternalServerError("Failed to build UserAnswers"))
                }
              }
            )
        }
    }

  private def buildRadioItems(
    currencies: Seq[(String, String, String)],
    msgs: Messages
  ): Seq[RadioItem] =
    currencies.zipWithIndex.flatMap { case ((name, _, symbol), idx) =>
      RefundingCurrency.values.find(_.toString.equalsIgnoreCase(name)).map { v =>
        RadioItem(
          content         = Text(msgs(s"refundingCurrency.${v.toString}", symbol)),
          value           = Some(v.toString),
          id              = Some(if (idx == 0) "value" else s"value_$idx"),
          label           = None,
          hint            = None,
          divider         = None,
          checked         = false,
          conditionalHtml = None,
          disabled        = false,
          attributes      = Map.empty
        )
      }
    }
}
