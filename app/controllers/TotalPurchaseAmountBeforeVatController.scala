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
import forms.TotalPurchaseAmountBeforeVatFormProvider
import models.{Mode, UserAnswers}
import navigation.Navigator
import pages.*
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.ConfigCurrencyMapping
import views.html.TotalPurchaseAmountBeforeVatView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TotalPurchaseAmountBeforeVatController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  configCurrencyMapping: ConfigCurrencyMapping,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: TotalPurchaseAmountBeforeVatFormProvider,
  val controllerComponents: MessagesControllerComponents,
  view: TotalPurchaseAmountBeforeVatView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[BigDecimal] = formProvider()

  private def backLink(mode: Mode)(userAnswers: UserAnswers) = userAnswers.get(RefundingCountryPage) match {
    case Some("DE") => routes.SupplierTaxNumberController.onPageLoad(mode)
    case _ =>
      userAnswers.get(SupplierVatRegistrationNumberPage) match {
        case Some(_) => routes.SupplierVatRegistrationNumberController.onPageLoad(mode)
        case None    => routes.SimplifiedInvoiceVatRegCheckController.onPageLoad(mode)
      }
  }

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>

    val preparedForm = request.userAnswers.get(TotalPurchaseAmountBeforeVatPage) match {
      case None        => form
      case Some(value) => form.fill(value)
    }

    val (currencyName, prefix) = resolveCurrency(request.userAnswers)
    Ok(view(preparedForm, mode, backLink(mode)(request.userAnswers), prefix, currencyName))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>

    form
      .bindFromRequest()
      .fold(
        formWithErrors => {
          val (currencyName, prefix) = resolveCurrency(request.userAnswers)
          Future.successful(BadRequest(view(formWithErrors, mode, backLink(mode)(request.userAnswers), prefix, currencyName)))
        },
        value =>
          for {
            updatedAnswers <- Future.fromTry(request.userAnswers.set(TotalPurchaseAmountBeforeVatPage, value))
            _              <- sessionRepository.set(updatedAnswers)
          } yield Redirect(navigator.nextPage(TotalPurchaseAmountBeforeVatPage, mode, updatedAnswers))
      )
  }

  private def humanizeName(name: String): String = {
    // split camelCase or words and capitalise each
    name
      .replaceAll("([a-z])([A-Z])", "$1 $2")
      .split("[ _-]+")
      .filter(_.nonEmpty)
      .map(s => s.head.toUpper.toString + s.tail)
      .mkString(" ")
  }

  private def resolveCurrency(userAnswers: models.UserAnswers): (String, String) = {
    val maybeCountry = userAnswers.get(pages.RefundingCountryPage).orElse {
      userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
        stored.split(",", 2).headOption.getOrElse(stored)
      }
    }

    val defaultSymbol = "€"
    maybeCountry match {
      case None => ("Euro", defaultSymbol)
      case Some(countryCode) =>
        val currencies = configCurrencyMapping.currenciesFor(countryCode)
        val chosen: Option[(String, String, String)] = userAnswers.get(RefundingCurrencyPage) match {
          case Some(currencyCode) => currencies.find(_._2 == currencyCode)
          case None               => currencies.headOption
        }
        chosen match {
          case Some((name, _, symbol)) => (humanizeName(name), symbol)
          case None                    => ("Euro", defaultSymbol)
        }
    }
  }

}
