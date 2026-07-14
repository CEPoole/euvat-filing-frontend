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
import forms.SupplierTaxNumberFormProvider

import javax.inject.Inject
import models.{InvoiceType, Mode, NormalMode, UserAnswers}
import navigation.Navigator
import pages.{InvoiceTypePage, RefundingCountryPage, SupplierTaxNumberPage}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents, Result}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.SupplierTaxNumberView

import scala.concurrent.{ExecutionContext, Future}

class SupplierTaxNumberController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: SupplierTaxNumberFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: SupplierTaxNumberView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form = formProvider()
  private val logger = Logger(getClass)

  private def backLink: Call = routes.SupplierAddressController.onPageLoad(NormalMode)

  private def resolveCountryCode(userAnswers: UserAnswers): Option[String] =
    userAnswers.get(pages.RefundingCountryPage).orElse {
      userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
        stored.split(",", 2).headOption.getOrElse(stored)
      }
    }

  private def requireGermany(userAnswers: UserAnswers): Option[Result] =
    resolveCountryCode(userAnswers) match {
      case Some("DE") => None
      case _ =>
        logger.warn("SupplierTaxNumberController - country is not Germany or missing from session, redirecting to JourneyRecovery")
        Some(Redirect(routes.JourneyRecoveryController.onPageLoad()))
    }

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>

    requireGermany(request.userAnswers).getOrElse {
      val preparedForm = request.userAnswers.get(SupplierTaxNumberPage) match {
        case None        => form
        case Some(value) => form.fill(value)
      }
      val isSimplifiedInvoice: Boolean = request.userAnswers.get(InvoiceTypePage).contains(InvoiceType.SimplifiedInvoice)
      Ok(view(preparedForm, mode, backLink, isSimplifiedInvoice))
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>

    requireGermany(request.userAnswers) match {
      case Some(result) => Future.successful(result)
      case None =>
        val isSimplifiedInvoice: Boolean = request.userAnswers.get(InvoiceTypePage).contains(InvoiceType.SimplifiedInvoice)

        form
          .bindFromRequest()
          .fold(
            formWithErrors => Future.successful(BadRequest(view(formWithErrors, mode, backLink, isSimplifiedInvoice))),
            value =>
              for {
                updatedAnswers <- Future.fromTry(request.userAnswers.set(SupplierTaxNumberPage, value))
                _              <- sessionRepository.set(updatedAnswers)
              } yield Redirect(navigator.nextPage(SupplierTaxNumberPage, mode, updatedAnswers))
          )
    }
  }
}
