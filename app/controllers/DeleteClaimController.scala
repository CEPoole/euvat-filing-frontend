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

import config.FrontendAppConfig
import controllers.actions.*
import forms.DeleteClaimFormProvider
import models.NormalMode
import navigation.Navigator
import pages.{DeleteClaimPage, RefundPeriodPage, RefundingCountryNamePage}
import play.api.i18n.{I18nSupport, Lang, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateTimeFormats.shortMonthYearFormat
import views.html.DeleteClaimView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DeleteClaimController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: DeleteClaimFormProvider,
  appConfig: FrontendAppConfig,
  val controllerComponents: MessagesControllerComponents,
  view: DeleteClaimView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form = formProvider()

  private def extractSummaryData(userAnswers: models.UserAnswers)(implicit messages: Messages, lang: Lang): (String, String, String) = {
    val memberState = userAnswers.get(RefundingCountryNamePage).getOrElse("")
    val startDate = userAnswers.get(RefundPeriodPage).map(_.startDate.format(shortMonthYearFormat())).getOrElse("")
    val endDate = userAnswers.get(RefundPeriodPage).map(_.endDate.format(shortMonthYearFormat())).getOrElse("")
    (memberState, startDate, endDate)
  }

  def onPageLoad: Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    implicit val messages: Messages = messagesApi.preferred(request)
    implicit val lang: Lang = messages.lang

    val (memberState, startDate, endDate) = extractSummaryData(request.userAnswers)

    Ok(view(form, memberState, startDate, endDate))
  }

  def onSubmit: Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    implicit val messages: Messages = messagesApi.preferred(request)
    implicit val lang: Lang = messages.lang

    form
      .bindFromRequest()
      .fold(
        formWithErrors => {
          val (memberState, startDate, endDate) = extractSummaryData(request.userAnswers)
          Future.successful(BadRequest(view(formWithErrors, memberState, startDate, endDate)))
        },
        value =>
          if (value) {
            // TODO: insert delete claim logic here for F2.9
            Future.successful(Redirect(appConfig.claimDashboardUrl))
          } else {
            Future.successful(Redirect(controllers.routes.TaskListDashboardController.onPageLoad()))
          }
      )
  }
}
