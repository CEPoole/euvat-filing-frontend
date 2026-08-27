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

import controllers.actions.{DataRequiredAction, DataRetrievalAction, IdentifierAction}
import pages.*
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{ConfigPurchaseMapping, CountryCode, CurrencyConfig}
import viewmodels.checkAnswers.CheckYourPurchaseDetailsSummary
import views.html.CheckYourPurchaseDetailsView

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class CheckYourPurchaseDetailsController @Inject() (
  override val messagesApi: MessagesApi,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  val controllerComponents: MessagesControllerComponents,
  view: CheckYourPurchaseDetailsView,
  currencyConfig: CurrencyConfig,
  configPurchaseMapping: ConfigPurchaseMapping,
  sessionRepository: SessionRepository
)(using ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    implicit val msgs: Messages = messagesApi.preferred(request)
    lazy val currencyList =
      CountryCode
        .findCountryCode(request.userAnswers)
        .map(currencyConfig.currencyConfig(_))
        .getOrElse(currencyConfig.default)

    val (maybeCurrencyDisplayName, maybeCurrencySymbol): (Option[String], Option[String]) =
      (request.userAnswers.get(RefundingCurrencyPage), currencyList) match
        case (_, currencyList) if currencyList.size > 1 =>
          Some(msgs("site.notProvided")) -> None
        case (Some(code), Seq(currency)) =>
          Some(msgs(s"refundingCurrency.${currency.code}", currency.symbol)) -> Some(currency.symbol)
        case _ =>
          (None, None)

    Ok(
      view(
        CheckYourPurchaseDetailsSummary
          .sections(
            request.userAnswers,
            maybeCurrencyDisplayName,
            maybeCurrencySymbol,
            configPurchaseMapping,
            currencyList.size > 1
          ),
        isPostSubmission = false,
        isAmended        = false
      )
    )
  }

  def onSubmit(): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    Redirect(controllers.routes.TaskListDashboardController.onPageLoad())
  }
}
