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

package controllers.purchase

import com.google.inject.Inject
import controllers.actions.{DataRequiredAction, DataRetrievalAction, IdentifierAction}
import play.api.i18n.{I18nSupport, MessagesApi}
import utils.{ConfigCurrencyMapping, ConfigPurchaseMapping}
import pages.*
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.CheckYourPurchaseDetailsView
import viewmodels.checkAnswers.CheckYourPurchaseDetailsSummary
import scala.concurrent.{ExecutionContext, Future}

class CheckYourPurchaseDetailsController @Inject() (
  override val messagesApi: MessagesApi,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  val controllerComponents: MessagesControllerComponents,
  view: CheckYourPurchaseDetailsView,
  configCurrencyMapping: ConfigCurrencyMapping,
  configPurchaseMapping: ConfigPurchaseMapping,
  sessionRepository: SessionRepository
)(using ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val maybeCountryCode = utils.CountryCode.findCountryCode(request.userAnswers)

    implicit val msgs = messagesApi.preferred(request)

    // Compute both a human readable display name (e.g. "Czech Koruna (Kč)")
    // and the raw currency symbol (e.g. "Kč"). The per-page amount views
    // use the raw symbol as a prefix; show the display name for the
    // currency row in the CYA.
    val (maybeCurrencyDisplayName, maybeCurrencySymbol): (Option[String], Option[String]) = request.userAnswers.get(RefundingCurrencyPage) match {
      case Some(code) =>
        val found = maybeCountryCode.toSeq.flatMap(configCurrencyMapping.currenciesFor).find(_._2 == code)
        val display = found.map { case (key, _code, symbol) => msgs(s"refundingCurrency.$key", symbol) }.orElse(Some(code))
        val symbol = found.map(_._3).orElse(Some(code))
        (display, symbol)
      case None =>
        // If the country requires an explicit currency selection (multiple
        // currencies configured) then show a currency CYA row even when the
        // user hasn't yet selected one. In that case present a "Not provided"
        // placeholder and do not prefix amounts with a symbol.
        maybeCountryCode
          .flatMap { c =>
            try {
              if (configCurrencyMapping.requiresCurrencySelection(c)) Some((Some(msgs("site.notProvided")), None))
              else None
            } catch {
              case _: Throwable => None
            }
          }
          .getOrElse((None, None))
    }

    val showCurrencyRow = maybeCountryCode.exists(code => configCurrencyMapping.requiresCurrencySelection(code))
    val sections = CheckYourPurchaseDetailsSummary.sections(request.userAnswers,
                                                            maybeCurrencyDisplayName,
                                                            maybeCurrencySymbol,
                                                            configPurchaseMapping,
                                                            showCurrencyRow
                                                           )
    Ok(view(sections, isPostSubmission = false, isAmended = false))
  }

  def onSubmit(): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // For now, mark a simple flag in session and redirect to task list (placeholder for Summary of purchases)
    val updated = request.userAnswers
    sessionRepository.set(updated).map(_ => Redirect(controllers.routes.TaskListDashboardController.onPageLoad()))
  }
}
