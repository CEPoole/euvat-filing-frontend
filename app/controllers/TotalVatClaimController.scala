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
import forms.TotalVatClaimFormProvider

import javax.inject.Inject
import models.Mode
import navigation.Navigator
import pages.{RefundingCurrencyPage, TotalVatClaimPage, TotalVatPaidPage}
import play.api.data.Form
import utils.ConfigCurrencyMapping
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.TotalVatClaimView

import scala.concurrent.{ExecutionContext, Future}

class TotalVatClaimController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: TotalVatClaimFormProvider,
  configCurrencyMapping: ConfigCurrencyMapping,
  val controllerComponents: MessagesControllerComponents,
  view: TotalVatClaimView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[BigDecimal] = formProvider()

  private def backLink(mode: Mode): Call = routes.TotalVatPaidController.onPageLoad(mode)

  private def resolveCurrencyPrefix(userAnswers: models.UserAnswers): String = {
    val maybeCountry = userAnswers.get(pages.RefundingCountryPage).orElse {
      userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
        stored.split(",", 2).headOption.getOrElse(stored)
      }
    }

    val defaultSymbol = "€"

    maybeCountry match {
      case None => defaultSymbol
      case Some(countryCode) =>
        userAnswers.get(RefundingCurrencyPage) match {
          case Some(currencyCode) =>
            configCurrencyMapping
              .currenciesFor(countryCode)
              .find(_._2 == currencyCode)
              .map(_._3)
              .getOrElse(configCurrencyMapping.currenciesFor(countryCode).headOption.map(_._3).getOrElse(defaultSymbol))
          case None =>
            configCurrencyMapping.currenciesFor(countryCode).headOption.map(_._3).getOrElse(defaultSymbol)
        }
    }
  }

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val preparedForm = request.userAnswers.get(TotalVatClaimPage) match {
      case None        => form
      case Some(value) => form.fill(value)
    }
    val currencySymbol = resolveCurrencyPrefix(request.userAnswers)
    Ok(view(preparedForm, mode, backLink(mode), currencySymbol))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val currencySymbol = resolveCurrencyPrefix(request.userAnswers)
    form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(view(formWithErrors, mode, backLink(mode), currencySymbol))),
        value =>
          for {
            updatedAnswers <- Future.fromTry(request.userAnswers.set(TotalVatClaimPage, value))
            _              <- sessionRepository.set(updatedAnswers)
          } yield {
            val totalVatPaid = request.userAnswers.get(TotalVatPaidPage).getOrElse(BigDecimal(0))
            if (value > totalVatPaid) {
              Redirect(routes.VatClaimWarningController.onPageLoad(mode))
            } else {
              Redirect(navigator.nextPage(TotalVatClaimPage, mode, updatedAnswers))
            }
          }
      )
  }
}
