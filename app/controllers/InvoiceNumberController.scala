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
import forms.InvoiceNumberFormProvider

import javax.inject.Inject
import models.{Mode, NormalMode}
import navigation.Navigator
import pages.InvoiceNumberPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.InvoiceNumberView
import play.api.mvc.Call

import scala.concurrent.{ExecutionContext, Future}

class InvoiceNumberController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: InvoiceNumberFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: InvoiceNumberView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form = formProvider()

  private def backLink(mode: Mode): Call = routes.InvoiceTypeController.onPageLoad(mode)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>

    val preparedForm = request.userAnswers.get(InvoiceNumberPage) match {
      case None        => form
      case Some(value) => form.fill(value)
    }

    Ok(view(preparedForm, mode, routes.InvoiceTypeController.onPageLoad(NormalMode)))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>

    form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(view(formWithErrors, mode, routes.InvoiceTypeController.onPageLoad(mode)))),
        value =>
          for {
            updatedAnswers <- Future.fromTry(request.userAnswers.set(InvoiceNumberPage, value))
            _              <- sessionRepository.set(updatedAnswers)
            result <- {
              // special flows when coming from the supplier tax identifier warning
              val wasShown = request.userAnswers.get(pages.SupplierTaxIdentifierWarningShownPage).contains(true)
              val previousInvoice = request.userAnswers.get(InvoiceNumberPage)

              if (wasShown) {
                if (previousInvoice.contains(value)) {
                  // invoice not changed -> show warning again
                  Future.successful(Redirect(routes.SupplierTaxIdentifierWarningController.onPageLoad(mode)))
                } else {
                  // invoice changed -> clear the flag and route to supplier tax id page
                  val cleared = updatedAnswers.remove(pages.SupplierTaxIdentifierWarningShownPage)
                  Future.fromTry(cleared).flatMap(ua => sessionRepository.set(ua).map(_ => Redirect(routes.SupplierTaxIdentifierNumberController.onPageLoad(mode))))
                }
              } else {
                Future.successful(Redirect(navigator.nextPage(InvoiceNumberPage, mode, updatedAnswers)))
              }
            }
          } yield result
      )
  }
}
