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
import models.{Mode, NormalMode, UserAnswers}
import navigation.Navigator
import pages.{RefundingCountryNamePage, RefundingCountryPage}
import play.api.Configuration
import play.api.data.FormError
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{ConfigCurrencyMapping, ConfigLanguageMapping, CountryList}
import models.RefundingLanguage
import views.html.RefundingCountryView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.http.UpstreamErrorResponse
import models.requests.LatestApplicationRequest
import services.EuVatRefundsService

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
  configCurrencyMapping: ConfigCurrencyMapping,
  val controllerComponents: MessagesControllerComponents,
  view: RefundingCountryView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with Logging {

  private def buildFormAndCountries() = {
    val countries = CountryList.fromConfig(config)
    val allowed: Set[String] = countries.flatMap { case (name, code) => Seq(name, code) }.toSet
    val form = formProvider(allowed)
    (countries, form)
  }

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val (countries, form) = buildFormAndCountries()

    // Determine a canonical country code to pre-fill the form.
    // Prefer the explicit `RefundingCountryPage` (code). If missing, try `RefundingCountryNamePage`:
    // - If it's stored as `code,name` use the code
    // - If it's stored as just the name, look up the code from the countries list
    val maybeCodeFromName = request.userAnswers.get(RefundingCountryNamePage).map { stored =>
      val parts = stored.split(",", 2)
      if (parts.length > 1) parts(0)
      else countries.find(_._1.equalsIgnoreCase(stored)).map(_._2).getOrElse(stored)
    }

    val maybeCode = request.userAnswers.get(RefundingCountryPage).orElse(maybeCodeFromName)

    val preparedForm = maybeCode.fold(form)(code => form.fill(code))

    Ok(view(preparedForm, countries, routes.TaskListDashboardController.onPageLoad(), mode))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>

    val (countries, form) = buildFormAndCountries()
    val baseAnswers: UserAnswers = request.userAnswers

    val boundResult = form
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
          val name = countries.find(_._2.equalsIgnoreCase(value)).map(_._1).getOrElse(value)

          val langs = configLanguageMapping.languagesFor(value).map(_.toLowerCase)

          val maybePrevCode = baseAnswers.get(RefundingCountryPage).orElse {
            baseAnswers.get(RefundingCountryNamePage).map(stored => stored.split(",", 2).headOption.getOrElse(stored))
          }

          def continueWithSave(updatedAnswers4: UserAnswers) =
            for {
              _ <- sessionRepository.set(updatedAnswers4)
            } yield Redirect(navigator.nextPage(RefundingCountryPage, mode, updatedAnswers4))

          val result = euVatRefundsService.retrieveTraderKnownFacts().flatMap { traderFacts =>
            implicit val hc = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

            val latestReq = LatestApplicationRequest(
              applicantVatRegNumber = traderFacts.vatRegNumber.toString,
              refundingCountry = Some(value),
              startDate = None,
              endDate = None,
              representativeId = None,
              maxNumber = 10000,
              orderBy = Some(0),
              sortOrder = Some("DESC"),
              startAt = Some(0)
            )

              euVatRefundsService.getLatestApplications(latestReq).flatMap { response =>

                // Validation applies when applicationStatus == "D" OR submissionStatus is null
                val isDuplicate = response.applications.exists { app =>
                  val statusIsD = app.applicationStatus.exists(_.equalsIgnoreCase("D"))
                  val submissionIsNull = app.submissionStatus.isEmpty
                  statusIsD || submissionIsNull
                }

                if (isDuplicate) {
                  // duplicate application - show error on the form
                  val formWithError = form.fill(value).withError("value", "refundingCountry.error.duplicate")
                  Future.successful(BadRequest(view(formWithError, countries, routes.TaskListDashboardController.onPageLoad(), mode)))
                } else {
                  // no duplicates - proceed with previous save flow
                  for {
                    updatedAnswers0 <- Future.fromTry(baseAnswers.set(RefundingCountryPage, value))
                    updatedAnswers1 <- Future.fromTry(updatedAnswers0.set(RefundingCountryNamePage, name))
                    updatedAnswers2 <- maybePrevCode match {
                                         case Some(prev) if !prev.equalsIgnoreCase(value) =>
                                           for {
                                             a <- Future.fromTry(updatedAnswers1.remove(pages.RefundingLanguagePage))
                                             b <- Future.fromTry(a.remove(pages.RefundingCurrencyPage))
                                             c <- Future.fromTry(b.set(pages.CountryChangedPage, true))
                                           } yield c
                                         case _ => Future.successful(updatedAnswers1)
                                       }
                    updatedAnswers3 <- if (langs.size == 1) {
                                         val langStr = langs.head
                                         val langModel = RefundingLanguage.values.find(_.toString == langStr).getOrElse(RefundingLanguage.English)
                                         Future.fromTry(updatedAnswers2.set(pages.RefundingLanguagePage, langModel))
                                       } else Future.successful(updatedAnswers2)
                    updatedAnswers4 <- {
                      val currencies = configCurrencyMapping.currenciesFor(value)
                      if (currencies.size == 1 && langs.size == 1)
                        Future.fromTry(updatedAnswers3.set(pages.RefundingCurrencyPage, currencies.head._2))
                      else
                        Future.successful(updatedAnswers3)
                    }
                    res <- continueWithSave(updatedAnswers4)
                  } yield res
                }
              }
          }

          result
        }
      )

    // Let any upstream/system errors bubble up to the global error handler
    boundResult
  }
}
