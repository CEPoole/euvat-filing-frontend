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
import forms.TotalVatPaidFormProvider

import javax.inject.Inject
import models.Mode
import navigation.Navigator
import utils.ConfigCurrencyMapping
import pages.{RefundingCurrencyPage, TotalPurchaseAmountBeforeVatPage, TotalVatPaidPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.TotalVatPaidView

import scala.concurrent.{ExecutionContext, Future}

class TotalVatPaidController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  configCurrencyMapping: ConfigCurrencyMapping,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: TotalVatPaidFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: TotalVatPaidView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[BigDecimal] = formProvider()

  private def backLink(mode: Mode) = routes.TotalPurchaseAmountBeforeVatController.onPageLoad(mode)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val preparedForm = request.userAnswers.get(TotalVatPaidPage) match {
      case None        => form
      case Some(value) => form.fill(value)
    }

    val (currencyName, prefix) = resolveCurrency(request.userAnswers)
    Ok(view(preparedForm, mode, backLink(mode), prefix, currencyName))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        formWithErrors => {
          val (currencyName, prefix) = resolveCurrency(request.userAnswers)
          Future.successful(BadRequest(view(formWithErrors, mode, backLink(mode), prefix, currencyName)))
        },
        value =>
          val totalPurchaseAmt: BigDecimal = request.userAnswers.get(TotalPurchaseAmountBeforeVatPage).getOrElse(BigDecimal(0))
          if (value >= totalPurchaseAmt) {
            Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad())) // TODO - redirect to warning page 5
          } else {
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(TotalVatPaidPage, value))
              _              <- sessionRepository.set(updatedAnswers)
            } yield Redirect(navigator.nextPage(TotalVatPaidPage, mode, updatedAnswers))
          }
      )
  }

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

  private def resolveCurrency(userAnswers: models.UserAnswers)(implicit request: play.api.mvc.Request[?]): (String, String) = {
    val maybeCountry = userAnswers.get(pages.RefundingCountryPage).orElse {
      userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
        stored.split(",", 2).headOption.getOrElse(stored)
      }
    }

    val defaultSymbol = "€"
    val defaultName = "Euro"

    maybeCountry match {
      case None => (defaultName, defaultSymbol)
      case Some(countryCode) =>
        val selection = userAnswers.get(RefundingCurrencyPage) match {
          case Some(currencyCode) =>
            configCurrencyMapping
              .currenciesFor(countryCode)
              .find(_._2 == currencyCode)
              .orElse(configCurrencyMapping.currenciesFor(countryCode).headOption)
          case None =>
            configCurrencyMapping.currenciesFor(countryCode).headOption
        }

        selection match {
          case Some((name, _code, symbol)) => (humanizeName(name), symbol)
          case _                           => (defaultName, defaultSymbol)
        }
    }
  }

  private def humanizeName(name: String): String = {
    name
      .replaceAll("([a-z])([A-Z])", "$1 $2")
      .split("[ _-]+")
      .filter(_.nonEmpty)
      .map(s => s.head.toUpper.toString + s.tail)
      .mkString(" ")
  }

}
