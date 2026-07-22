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
import forms.RefundingCurrencyFormProvider

import javax.inject.Inject
import models.{Mode, RefundingCurrency, UserAnswers}
import navigation.Navigator
import pages.RefundingCurrencyPage
import play.api.Logger
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request}
import repositories.SessionRepository
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{ConfigCurrencyMapping, ConfigLanguageMapping}
import views.html.RefundingCurrencyView

import scala.concurrent.{ExecutionContext, Future}

class RefundingCurrencyController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: RefundingCurrencyFormProvider,
  configCurrencyMapping: ConfigCurrencyMapping,
  configLanguageMapping: ConfigLanguageMapping,
  val controllerComponents: MessagesControllerComponents,
  view: RefundingCurrencyView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[RefundingCurrency] = formProvider()
  private val logger = Logger(getClass)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    resolveCountryCode(request.userAnswers) match {
      case None =>
        logger.warn("RefundingCurrencyController.onPageLoad - no refunding country in session, redirecting to JourneyRecovery")
        Redirect(routes.JourneyRecoveryController.onPageLoad())
      case Some(countryCode) =>
        val currencies = configCurrencyMapping.currenciesFor(countryCode)
        val msgs = messagesApi.preferred(request)
        val items = buildRadioItems(currencies, msgs)
        val preparedForm = request.userAnswers
          .get(RefundingCurrencyPage)
          .flatMap { storedCode =>
            currencies.find(_._2 == storedCode).map { case (name, _, _) =>
              form.fill(RefundingCurrency.values.find(_.toString == name).getOrElse(RefundingCurrency.Euro))
            }
          }
          .getOrElse(form)
        // Determine back link: if the country has only one language then the language page may be skipped; link back to country
        val back =
          if (configLanguageMapping.languagesFor(countryCode).size <= 1) routes.RefundingCountryController.onPageLoad(mode)
          else routes.RefundingLanguageController.onPageLoad(mode)

        Ok(view(preparedForm, items, back, mode))
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        formWithErrors =>
          resolveCountryCode(request.userAnswers) match {
            case None =>
              logger.warn(
                "RefundingCurrencyController.onSubmit - no refunding country in session while binding form errors; redirecting to JourneyRecovery"
              )
              Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
            case Some(countryCode) =>
              val currencies = configCurrencyMapping.currenciesFor(countryCode)
              val msgs = messagesApi.preferred(request)
              val items = buildRadioItems(currencies, msgs)
              val back =
                if (configLanguageMapping.languagesFor(countryCode).size <= 1) routes.RefundingCountryController.onPageLoad(mode)
                else routes.RefundingLanguageController.onPageLoad(mode)
              Future.successful(BadRequest(view(formWithErrors, items, back, mode)))
          },
        value =>
          resolveCountryCode(request.userAnswers) match {
            case None =>
              logger.warn("RefundingCurrencyController.onSubmit - no refunding country in session; redirecting to JourneyRecovery")
              Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
            case Some(countryCode) =>
              val currencies = configCurrencyMapping.currenciesFor(countryCode)
              currencies.find(_._1.equalsIgnoreCase(value.toString)).map(_._2) match {
                case None =>
                  logger.warn(s"RefundingCurrencyController.onSubmit - could not find currency code for ${value.toString}")
                  Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
                case Some(currencyCode) =>
                  val isChanged = request.userAnswers.get(RefundingCurrencyPage) match {
                    case Some(existing) => existing != currencyCode
                    case None           => true
                  }
                  for {
                    updatedAnswers <- Future.fromTry(request.userAnswers.set(RefundingCurrencyPage, currencyCode))
                    updatedAnswers2 <- if (isChanged && request.userAnswers.get(pages.ClaimDetailsCompletedPage).contains(true))
                                         Future.fromTry(updatedAnswers.set(pages.ClaimDetailsAmendedPage, true))
                                       else
                                         Future.successful(updatedAnswers)
                    _ <- sessionRepository.set(updatedAnswers2)
                  } yield Redirect(navigator.nextPage(RefundingCurrencyPage, mode, updatedAnswers2))
              }
          }
      )
  }

  private def resolveCountryCode(userAnswers: UserAnswers): Option[String] =
    userAnswers.get(pages.RefundingCountryPage).orElse {
      userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
        stored.split(",", 2).headOption.getOrElse(stored)
      }
    }

  private def buildRadioItems(
    currencies: Seq[(String, String, String)],
    msgs: play.api.i18n.Messages
  ): Seq[uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem] =
    currencies.zipWithIndex.flatMap { case ((name, _, symbol), idx) =>
      RefundingCurrency.values.find(_.toString.equalsIgnoreCase(name)).map { v =>
        uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem(
          content = Text(msgs(s"refundingCurrency.${v.toString}", symbol)),
          value   = Some(v.toString),
          id      = Some(if (idx == 0) "value" else s"value_$idx")
        )
      }
    }
}
