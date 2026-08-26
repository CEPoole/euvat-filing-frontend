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
import forms.RefundingCountryFormProvider
import models.requests.LatestApplicationRequest
import models.{Mode, RefundingLanguage, UserAnswers}
import navigation.Navigator
import pages.{CountryChangedPage, PurchaseSubCategoryLabelPage, PurchaseSubCategoryPage, PurchaseSubTypeLabelPage, PurchaseSubTypePage, PurchaseTypePage, RefundingCountryNamePage, RefundingCountryPage, RefundingCurrencyPage, RefundingLanguagePage}
import play.api.data.{Form, FormError}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.*
import play.api.{Configuration, Logging}
import queries.LatestCountryResponseQuery
import repositories.SessionRepository
import services.EuVatRefundsService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{CurrencyConfig, ConfigLanguageMapping, CountryCode, CountryList}
import views.html.RefundingCountryView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class RefundingCountryController @Inject() (
                                             override val messagesApi: MessagesApi,
                                             sessionRepository: SessionRepository,
                                             navigator: Navigator,
                                             identify: IdentifierAction,
                                             getData: DataRetrievalAction,
                                             requireData: DataRequiredAction,
                                             euVatRefundsService: EuVatRefundsService,
                                             formProvider: RefundingCountryFormProvider,
                                             config: Configuration,
                                             configLanguageMapping: ConfigLanguageMapping,
                                             currencyConfig: CurrencyConfig,
                                             val controllerComponents: MessagesControllerComponents,
                                             view: RefundingCountryView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with Logging {

  private def buildFormAndCountries() = {
    val countries = CountryList.fromConfig(config)
    val allowed: Set[String] = countries.flatMap { case (name, code) => Seq(name, code) }.toSet
    (countries, formProvider(allowed))
  }

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val (countries, form) = buildFormAndCountries()
    val countryCode = CountryCode.findCountryCode(request.userAnswers)
    val preparedForm = countryCode.fold(form)(code => form.fill(code))
    Ok(view(preparedForm, countries, routes.TaskListDashboardController.onPageLoad(), mode))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val (countries, form) = buildFormAndCountries()

    form
      .bindFromRequest()
      .fold(
        formWithErrors => {
          val typed = request.body.asFormUrlEncoded.flatMap(_.get("valueTyped").flatMap(_.headOption)).getOrElse("")
          val adjustedForm = if (typed.trim.nonEmpty) {
            val filtered = formWithErrors.errors.filterNot(e => e.key == "value" && e.message == "refundingCountry.error.required")
            formWithErrors.copy(errors = filtered :+ FormError("value", "refundingCountry.error.invalid"))
          } else {
            formWithErrors
          }
          Future.successful(BadRequest(view(adjustedForm, countries, routes.TaskListDashboardController.onPageLoad(), mode)))
        },
        value => {
          val latestReq = LatestApplicationRequest(
            applicantVatRegNumber = request.identifierValue,
            refundingCountry      = Some(value)
          )

          euVatRefundsService
            .getLatestApplications(latestReq)
            .flatMap { latestResponse =>
              // If a record exists total application will return > 0 which is a duplicate
              if (latestResponse.totalApplication > 0) {
                // duplicate application - show error on the form
                val formWithError = form.fill(value).withError("value", "refundingCountry.error.duplicate")
                Future.successful(BadRequest(view(formWithError, countries, routes.TaskListDashboardController.onPageLoad(), mode)))
              } else {
                val baseAnswers: UserAnswers = request.userAnswers
                val countryName = countries.find(_._2.equalsIgnoreCase(value)).map(_._1).getOrElse(value)
                val languages = configLanguageMapping.languagesFor(value).map(_.toLowerCase)
                val prevCountryCode = CountryCode.findCountryCode(baseAnswers)

                // no duplicates - proceed with save flow (note: on country change only clear language/currency)
                for {
                  updatedAnswers  <- Future.fromTry(baseAnswers.set(RefundingCountryPage, value))
                  updatedAnswers0 <- Future.fromTry(updatedAnswers.set(LatestCountryResponseQuery, latestResponse))
                  updatedAnswers1 <- Future.fromTry(updatedAnswers0.set(RefundingCountryNamePage, countryName))
                  updatedAnswers2 <- prevCountryCode match {
                                       case Some(prev) if !prev.equalsIgnoreCase(value) =>
                                         // If country changes, clear below data from session
                                         List(
                                           RefundingLanguagePage,
                                           RefundingCurrencyPage,
                                           PurchaseTypePage,
                                           PurchaseSubTypePage,
                                           PurchaseSubTypeLabelPage,
                                           PurchaseSubCategoryPage,
                                           PurchaseSubCategoryLabelPage
                                         ).foldLeft(Future.successful(updatedAnswers1)) { (answersF, page) =>
                                           answersF.flatMap(answers => Future.fromTry(answers.remove(page)))
                                         }.flatMap(answers => Future.fromTry(answers.set(CountryChangedPage, true)))
                                       case _ => Future.successful(updatedAnswers1)
                                     }
                  updatedAnswers3 <- if (languages.size == 1) {
                                       val langStr = languages.head
                                       val langModel = RefundingLanguage.values.find(_.toString == langStr).getOrElse(RefundingLanguage.English)
                                       Future.fromTry(updatedAnswers2.set(RefundingLanguagePage, langModel))
                                     } else { Future.successful(updatedAnswers2) }
                  updatedAnswers4 <- {
                    val currencies = currencyConfig.currencyConfig(value)
                    // If the country has a single configured currency persist it into session
                    // regardless of the number of available languages. This covers cases
                    // where a country (e.g. BE) has multiple languages but only one currency.
                    if (currencies.size == 1) {
                      Future.fromTry(updatedAnswers3.set(RefundingCurrencyPage, currencies.head._2))
                    } else {
                      Future.successful(updatedAnswers3)
                    }
                  }
                  result <- saveAndRedirect(updatedAnswers4, value, form, countries, mode)
                } yield result
              }
            }
            .recover { case NonFatal(e) =>
              logger.error("Failed to retrieve data from backend", e)
              Redirect(routes.JourneyRecoveryController.onPageLoad())
            }

        }
      )

  }

  private def saveAndRedirect(answers: UserAnswers, value: String, form: Form[String], countries: Seq[(String, String)], mode: Mode)(using
    Request[?]
  ): Future[Result] =
    sessionRepository
      .set(answers)
      .map(_ => Redirect(navigator.nextPage(RefundingCountryPage, mode, answers)))
      .recover { case NonFatal(_) =>
        BadRequest(view(form.fill(value), countries, routes.TaskListDashboardController.onPageLoad(), mode))
      }

}
