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
import models.{CheckMode, Mode, NormalMode}
import navigation.Navigator
import pages.{SupplierVatRegistrationNumberPage, VrnWarningFlowPage}

import javax.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.SupplierVrnWarningView

import scala.concurrent.{ExecutionContext, Future}

class SupplierVrnWarningController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  navigator: Navigator,
  val controllerComponents: MessagesControllerComponents,
  view: SupplierVrnWarningView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    for {
      updated <- Future.fromTry(request.userAnswers.set(VrnWarningFlowPage, true))
      _       <- sessionRepository.set(updated)
    } yield Ok(view(routes.SupplierVatRegistrationNumberController.onPageLoad(mode), mode))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    for {
      cleared <- Future.fromTry(request.userAnswers.remove(VrnWarningFlowPage))
      _       <- sessionRepository.set(cleared)
    } yield {
      if (mode == CheckMode) {
        Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
      } else {
        Redirect(navigator.nextPage(SupplierVatRegistrationNumberPage, mode, cleared))
      }
    }
  }
}
