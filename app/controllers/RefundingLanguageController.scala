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
import forms.RefundingLanguageFormProvider
import models.{Mode, RefundingLanguage}
import navigation.Navigator
import pages.*
import play.api.Logger
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.*
import views.html.RefundingLanguageView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RefundingLanguageController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: RefundingLanguageFormProvider,
  configLanguageMapping: ConfigLanguageMapping,
  configCurrencyMapping: ConfigCurrencyMapping,
  val controllerComponents: MessagesControllerComponents,
  view: RefundingLanguageView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  val form: Form[RefundingLanguage] = formProvider()
  private val logger = Logger(getClass)

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    // Data guard: require a previously selected refunding country. Support two storage formats:
    // - `RefundingCountryPage` contains the country code
    // - `RefundingCountryNamePage` may contain a delimited string where the code is first ("code,name")
    CountryCode.findCountryCode(request.userAnswers) match {
      case None =>
        logger.warn("RefundingLanguageController.onPageLoad - no refunding country in session, redirecting to JourneyRecovery")
        Redirect(routes.JourneyRecoveryController.onPageLoad())
      case Some(countryStored) =>
        val preparedForm = request.userAnswers.get(RefundingLanguagePage) match {
          case None        => form
          case Some(value) => form.fill(value)
        }
        val langs = configLanguageMapping.languagesFor(countryStored)
        val msgs = messagesApi.preferred(request)
        val items = langs.zipWithIndex.flatMap { case (lang, idx) =>
          RefundingLanguage.values.find(_.toString.equalsIgnoreCase(lang)).map { v =>
            RadioItem(
              content = uk.gov.hmrc.govukfrontend.views.Aliases.Text(msgs(s"refundingLanguage.${v.toString}")),
              value   = Some(v.toString),
              id      = Some(s"value_$idx")
            )
          }
        }
        Ok(view(preparedForm, items, routes.RefundingCountryController.onPageLoad(mode), mode))
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        formWithErrors =>
          // need country code to rebuild options; support either RefundingCountryPage or delimited RefundingCountryNamePage
          CountryCode.findCountryCode(request.userAnswers) match {
            case None =>
              logger.warn(
                "RefundingLanguageController.onSubmit - no refunding country in session while binding form errors; redirecting to JourneyRecovery"
              )
              Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
            case Some(countryCode) =>
              val langs = configLanguageMapping.languagesFor(countryCode)
              val msgs = messagesApi.preferred(request)
              val items = langs.zipWithIndex.flatMap { case (lang, idx) =>
                RefundingLanguage.values.find(_.toString.equalsIgnoreCase(lang)).map { v =>
                  RadioItem(
                    content = Text(msgs(s"refundingLanguage.${v.toString}")),
                    value   = Some(v.toString),
                    id      = Some(s"value_$idx")
                  )
                }
              }
              Future.successful(BadRequest(view(formWithErrors, items, routes.RefundingCountryController.onPageLoad(mode), mode)))
          },
        value =>
          val isChanged = request.userAnswers.get(RefundingLanguagePage) match {
            case Some(existing) => existing != value
            case None           => true
          }
          for {
            updatedAnswers <- Future.fromTry(request.userAnswers.set(RefundingLanguagePage, value))
            updatedAnswers1 <- if (isChanged && request.userAnswers.get(pages.ClaimDetailsCompletedPage).contains(true)) {
                                 Future.fromTry(updatedAnswers.set(pages.ClaimDetailsAmendedPage, true))
                               } else {
                                 Future.successful(updatedAnswers)
                               }
            _ <- sessionRepository.set(updatedAnswers1)
          } yield Redirect(navigator.nextPage(RefundingLanguagePage, mode, updatedAnswers1))
      )
  }
}
