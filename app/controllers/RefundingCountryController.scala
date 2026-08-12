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
import pages.{RefundingCountryNamePage, RefundingCountryPage}
import play.api.data.{Form, FormError}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.*
import play.api.{Configuration, Logging}
import repositories.SessionRepository
import services.EuVatRefundsService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import utils.{ConfigCurrencyMapping, ConfigLanguageMapping, CountryCode, CountryList}
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
      if (parts.length > 1) { parts(0) }
      else { countries.find(_._1.equalsIgnoreCase(stored)).map(_._2).getOrElse(stored) }
    }

    val maybeCode = request.userAnswers.get(RefundingCountryPage).orElse(maybeCodeFromName)
    val preparedForm = maybeCode.fold(form)(code => form.fill(code))
    Ok(view(preparedForm, countries, routes.TaskListDashboardController.onPageLoad(), mode))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val (countries, form) = buildFormAndCountries()
    val baseAnswers: UserAnswers = request.userAnswers

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
          val maybePrevCode = CountryCode.findCountryCode(baseAnswers)

          euVatRefundsService
            .retrieveTraderKnownFacts()
            .flatMap { traderFacts =>
              val latestReq = LatestApplicationRequest(
                applicantVatRegNumber = traderFacts.vatRegNumber.toString,
                refundingCountry      = Some(value),
                startDate             = None,
                endDate               = None,
                representativeId      = None,
                maxNumber             = 10000,
                orderBy               = Some(0),
                sortOrder             = Some("DESC"),
                startAt               = Some(0)
              )

              euVatRefundsService.getLatestApplications(latestReq).flatMap { response =>
                // Validation applies when applicationStatus == "D" OR submissionStatus is null
//                val isDuplicate = response.applications.exists { app =>
//                  val statusIsD = app.applicationStatus.exists(_.equalsIgnoreCase("D"))
//                  val submissionIsNull = app.submissionStatus.isEmpty
//                  statusIsD || submissionIsNull
//                }

                if (response.totalApplication > 0) {
                  // duplicate application - show error on the form
                  val formWithError = form.fill(value).withError("value", "refundingCountry.error.duplicate")
                  Future.successful(BadRequest(view(formWithError, countries, routes.TaskListDashboardController.onPageLoad(), mode)))
                } else {
                  val countryName = countries.find(_._2.equalsIgnoreCase(value)).map(_._1).getOrElse(value)
                  val languages = configLanguageMapping.languagesFor(value).map(_.toLowerCase)

                  // no duplicates - proceed with save flow (note: on country change only clear language/currency)
                  for {
                    updatedAnswers0 <- Future.fromTry(baseAnswers.set(RefundingCountryPage, value))
                    updatedAnswers1 <- Future.fromTry(updatedAnswers0.set(RefundingCountryNamePage, countryName))
                    updatedAnswers2 <- maybePrevCode match {
                                         case Some(prev) if !prev.equalsIgnoreCase(value) =>
                                           for {
                                             a <- Future.fromTry(updatedAnswers1.remove(pages.RefundingLanguagePage))
                                             b <- Future.fromTry(a.remove(pages.RefundingCurrencyPage))
                                             // If the refunding country has changed, clear any previously selected
                                             // purchase type and related sub-type / sub-category selections so
                                             // they cannot conflict with the new country's mappings.
                                             c <- Future.fromTry(b.remove(pages.PurchaseTypePage))
                                             d <- Future.fromTry(c.remove(pages.PurchaseSubTypePage))
                                             e <- Future.fromTry(d.remove(pages.PurchaseSubTypeLabelPage))
                                             f <- Future.fromTry(e.remove(pages.PurchaseSubCategoryPage))
                                             g <- Future.fromTry(f.remove(pages.PurchaseSubCategoryLabelPage))
                                             h <- Future.fromTry(g.set(pages.CountryChangedPage, true))
                                           } yield h
                                         case _ => Future.successful(updatedAnswers1)
                                       }
                    updatedAnswers3 <- if (languages.size == 1) {
                                         val langStr = languages.head
                                         val langModel = RefundingLanguage.values.find(_.toString == langStr).getOrElse(RefundingLanguage.English)
                                         Future.fromTry(updatedAnswers2.set(pages.RefundingLanguagePage, langModel))
                                       } else { Future.successful(updatedAnswers2) }
                    updatedAnswers4 <- {
                      val currencies = configCurrencyMapping.currenciesFor(value)
                      if (currencies.size == 1 && languages.size == 1) {
                        Future.fromTry(updatedAnswers3.set(pages.RefundingCurrencyPage, currencies.head._2))
                      } else {
                        Future.successful(updatedAnswers3)
                      }
                    }
                    result <- saveAndRedirect(updatedAnswers4, value, form, countries, mode)
                  } yield result
                }
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
