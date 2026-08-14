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
import forms.SupplierTaxIdentifierNumberFormProvider
import models.Mode
import models.requests.SupplierTaxIdentifierCountRequest
import models.responses.SupplierTaxIdentifierCountResponse
import models.responses.AddPurchaseResponse
import pages.{AddPurchaseResponsePage, InvoiceNumberPage}
import queries.ClaimApplicationResponseQuery
import pages.SupplierTaxIdentifierWarningShownPage
import services.EuVatRefundsService
import navigation.Navigator
import pages.{SupplierTaxIdentifierNumberPage, SupplierVatRegistrationNumberPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.SupplierTaxIdentifierNumberView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SupplierTaxIdentifierNumberController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  euVatRefundsService: EuVatRefundsService,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: SupplierTaxIdentifierNumberFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: SupplierTaxIdentifierNumberView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[String] = formProvider()

  private def backLink(mode: Mode) = routes.SupplierTaxNumberController.onPageLoad(mode)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    for {
      updatedAnswers <- Future.fromTry(request.userAnswers.remove(SupplierVatRegistrationNumberPage))
      _              <- sessionRepository.set(updatedAnswers)
    } yield None
    val preparedForm = request.userAnswers.get(SupplierTaxIdentifierNumberPage).fold(form)(form.fill)
    Ok(view(preparedForm, mode, backLink(mode)))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(view(formWithErrors, mode, backLink(mode)))),
        value =>
          for {
            updatedAnswers <- Future.fromTry(request.userAnswers.set(SupplierTaxIdentifierNumberPage, value))
            _              <- sessionRepository.set(updatedAnswers)
            result <- {
              val maybeAppId = updatedAnswers.get(ClaimApplicationResponseQuery).map(_.applicationId.toLong)
              val maybeItem  = updatedAnswers.get(AddPurchaseResponsePage).map(_.itemNumber)
              val invoiceNum  = updatedAnswers.get(InvoiceNumberPage).getOrElse("")

              (maybeAppId, maybeItem) match {
                case (Some(appId), Some(itemNumber)) =>
                  val countF = euVatRefundsService.getSupplierTaxIdentifierCount(SupplierTaxIdentifierCountRequest(appId, itemNumber, value, invoiceNum))
                  countF.flatMap {
                    case SupplierTaxIdentifierCountResponse(count) if count > 0 =>
                      Future.successful(Redirect(routes.SupplierTaxIdentifierWarningController.onPageLoad(mode)))
                    case _ =>
                      val cleared = updatedAnswers.remove(SupplierTaxIdentifierWarningShownPage)
                      Future.fromTry(cleared).flatMap(ua => sessionRepository.set(ua).map(_ => Redirect(navigator.nextPage(SupplierTaxIdentifierNumberPage, mode, ua))))
                  }.recover { case _ => Redirect(routes.JourneyRecoveryController.onPageLoad()) }

                case _ => Future.successful(Redirect(navigator.nextPage(SupplierTaxIdentifierNumberPage, mode, updatedAnswers)))
              }
            }
          } yield result
      )
  }

}
