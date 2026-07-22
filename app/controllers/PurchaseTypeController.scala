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
import models.requests.DataRequest
import forms.PurchaseTypeFormProvider
import models.{Mode, PurchaseType}
import navigation.Navigator
import pages.PurchaseTypePage
import pages.SimplifiedInvoiceVatRegCheckPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.PurchaseTypeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PurchaseTypeController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: PurchaseTypeFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: PurchaseTypeView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[PurchaseType] = formProvider()

  private def backLink(mode: Mode)(implicit request: DataRequest[_]) =
    routes.BeforeYouStartPurchaseController.onPageLoad()

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val preparedForm = request.userAnswers.get(PurchaseTypePage).fold(form)(form.fill)

    Ok(view(preparedForm, mode, backLink(mode)))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(view(formWithErrors, mode, backLink(mode)))),
        value =>
          for {
            updatedAnswers <- Future.fromTry(request.userAnswers.set(PurchaseTypePage, value))
            _              <- sessionRepository.set(updatedAnswers)
          } yield Redirect(navigator.nextPage(PurchaseTypePage, mode, updatedAnswers))
      )
  }
}
