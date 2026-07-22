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
import forms.{RefundPeriodData, RefundPeriodFormProvider}
import models.requests.{DataRequest, LatestApplicationRequest}
import models.responses.TraderKnownFactsResponse
import models.{Mode, RefundPeriod}
import navigation.Navigator
import pages.RefundPeriodPage
import play.api.Logging
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.*
import queries.TraderKnownFactsQuery
import repositories.SessionRepository
import services.EuVatRefundsService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{ConfigCurrencyMapping, ConfigLanguageMapping}
import views.html.RefundPeriodView

import java.time.{LocalDateTime, YearMonth}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RefundPeriodController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: RefundPeriodFormProvider,
  euVatRefundsService: EuVatRefundsService,
  configCurrencyMapping: ConfigCurrencyMapping,
  configLanguageMapping: ConfigLanguageMapping,
  val controllerComponents: MessagesControllerComponents,
  view: RefundPeriodView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with Logging {

  private def errorMessage(form: Form[RefundPeriodData], keys: Seq[String])(implicit messages: Messages): Option[String] = {
    val errors = form.errors.filter(e => keys.contains(e.key))
    if (errors.isEmpty) { None }
    else { Some(errors.map(e => messages(e.message, e.args*)).mkString("<br>")) }
  }

  private def errorLinkOverrides(form: Form[RefundPeriodData]): Map[String, String] = Map(
    ""                           -> s"${form("start").id}.month",
    "start"                      -> s"${form("start").id}.month",
    "end"                        -> s"${form("end").id}.month",
    s"${form("start").id}.year"  -> s"${form("start").id}.year",
    s"${form("end").id}.year"    -> s"${form("end").id}.year",
    s"${form("start").id}.month" -> s"${form("start").id}.month",
    s"${form("end").id}.month"   -> s"${form("end").id}.month"
  )

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val preparedForm = request.userAnswers.get(RefundPeriodPage) match {
      case None => formProvider()
      case Some(value) =>
        val start = java.time.YearMonth.of(value.startDate.getYear, value.startDate.getMonthValue)
        val end = java.time.YearMonth.of(value.endDate.getYear, value.endDate.getMonthValue)
        formProvider().fill(RefundPeriodData(start, end))
    }
    val (mappedForm, highlighted) = formProvider.withMappedErrors(preparedForm)
    val startMsg = errorMessage(mappedForm, Seq("start", "start.month", "start.year"))
    val endMsg = errorMessage(mappedForm, Seq("end", "end.month", "end.year"))
    Ok(view(mappedForm, mode, backLink(mode), startMsg, endMsg, highlighted, errorLinkOverrides(mappedForm)))
  }

  private def renderError(form: Form[RefundPeriodData], mode: Mode)(implicit request: DataRequest[AnyContent], messages: Messages) = {
    val (mappedForm, highlighted) = formProvider.withMappedErrors(form)
    val startMsg = errorMessage(mappedForm, Seq("start", "start.month", "start.year"))
    val endMsg = errorMessage(mappedForm, Seq("end", "end.month", "end.year"))

    Future.successful(
      BadRequest(
        view(mappedForm, mode, backLink(mode), startMsg, endMsg, highlighted, errorLinkOverrides(mappedForm))
      )
    )
  }

  // Business Function F6 check
  private def isStartDateValid(startDate: LocalDateTime, regDate: LocalDateTime): (Boolean, String) = {
    val reg = YearMonth.from(regDate)
    val regMonth = reg.getMonthValue
    // Case 1: Jan–Mar rule
    if (regMonth >= 1 && regMonth <= 3) {
      // Same month/year OR after regDate (same year)
      (startDate.equals(regDate) || startDate.isAfter(regDate), "refundPeriod.start.error.beforeVatRegDate.firstQuarter")
    } else { // Case 2: Apr–Dec rule
      val min = regDate.minusMonths(3)
      (!startDate.isBefore(min) || startDate.isAfter(regDate), "refundPeriod.start.error.beforeVatRegDate.remainingQuarter")
    }
  }

  private def saveAndRedirect(
    traderResponse: TraderKnownFactsResponse,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    mode: Mode
  )(using request: DataRequest[?], ec: ExecutionContext): Future[Result] = {
    val refundPeriod = RefundPeriod(startDate, endDate)

    val isChanged = request.userAnswers.get(RefundPeriodPage) match {
      case Some(existing) => existing.startDate != startDate || existing.endDate != endDate
      case None           => true
    }

    for {
      updatedAnswer1 <- Future.fromTry(request.userAnswers.set(TraderKnownFactsQuery, traderResponse))
      updatedAnswer2 <- Future.fromTry(updatedAnswer1.set(RefundPeriodPage, refundPeriod))
      updatedAnswer3 <- Future.fromTry(updatedAnswer2.remove(pages.CountryChangedPage))
      updatedAnswer4 <- if (isChanged && request.userAnswers.get(pages.ClaimDetailsCompletedPage).contains(true))
                          Future.fromTry(updatedAnswer3.set(pages.ClaimDetailsAmendedPage, true))
                        else
                          Future.successful(updatedAnswer3)
      _ <- sessionRepository.set(updatedAnswer4)
    } yield Redirect(navigator.nextPage(RefundPeriodPage, mode, updatedAnswer4))
  }

  private def checkOverlappingPeriod(
    traderResponse: TraderKnownFactsResponse,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    mode: Mode
  )(using request: DataRequest[?], ec: ExecutionContext): Future[Result] = {
    if (endDate.getMonthValue == 12) {
      saveAndRedirect(traderResponse, startDate, endDate, mode)
    } else {
      val refundingCountry = request.userAnswers.get(pages.RefundingCountryPage).orElse {
        request.userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
          stored.split(",", 2).headOption.getOrElse(stored)
        }
      }
      val latestApplicationRequest = LatestApplicationRequest(
        applicantVatRegNumber = traderResponse.vatRegNumber.toString,
        refundingCountry      = refundingCountry,
        startDate             = Some(startDate),
        endDate               = Some(endDate),
        representativeId      = None,
        maxNumber             = 100,
        orderBy               = None,
        sortOrder             = None,
        startAt               = None
      )
      euVatRefundsService.getLatestApplications(latestApplicationRequest).flatMap { response =>

        if (response.applications.nonEmpty) {
          // TODO: redirect to warning page once designed — showing error as placeholder
          given DataRequest[AnyContent] = request.asInstanceOf[DataRequest[AnyContent]]
          given Messages = messagesApi.preferred(request)
          val formWithError = formProvider()
            .fill(RefundPeriodData(YearMonth.from(startDate), YearMonth.from(endDate)))
            .withError("start", "refundPeriod.error.overlap")
          renderError(formWithError, mode)
        } else {
          logger.info(s"F5 overlap check: no overlapping applications found, startDate=$startDate, endDate=$endDate")
          saveAndRedirect(traderResponse, startDate, endDate, mode)
        }
      }
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val baseForm = formProvider()

    baseForm
      .bindFromRequest()
      .fold(
        formWithErrors => renderError(formWithErrors, mode),
        value =>
          euVatRefundsService.retrieveTraderKnownFacts().flatMap { traderResponse =>
            val startDate = YearMonth.of(value.start.getYear, value.start.getMonthValue).atDay(1).atStartOfDay()
            val endDate = YearMonth.of(value.end.getYear, value.end.getMonthValue).atEndOfMonth().atTime(23, 59, 59, 999000000)

            val maybeErrorForm: Option[Form[RefundPeriodData]] = (traderResponse.dateOfRegistration, traderResponse.dateOfDeregistration) match {
              case (Some(regDate), Some(deRegDate)) =>
                val (validStartDate, msg) = isStartDateValid(startDate, regDate)
                if (!validStartDate) {
                  Some(baseForm.fill(value).withError("start", msg))
                } else if (endDate.isAfter(deRegDate)) {
                  Some(baseForm.fill(value).withError("end", "refundPeriod.end.error.afterVatDeRegDate"))
                } else {
                  None
                }
              case (Some(regDate), None) =>
                val (validStartDate, msg) = isStartDateValid(startDate, regDate)
                if (!validStartDate) {
                  Some(baseForm.fill(value).withError("start", msg))
                } else {
                  None
                }
              case (None, Some(deRegDate)) =>
                if (endDate.isAfter(deRegDate)) {
                  Some(baseForm.fill(value).withError("end", "refundPeriod.end.error.afterVatDeRegDate"))
                } else {
                  None
                }
              // Missing reg or deReg dates → proceed normally and skip navigation
              case _ => None
            }

            // Only apply mapped errors when they exist
            maybeErrorForm.foreach(formProvider.withMappedErrors)
            maybeErrorForm match {
              case Some(formWithError) => renderError(formWithError, mode)
              case None                => checkOverlappingPeriod(traderResponse, startDate, endDate, mode)
            }
          }
      )
  }

  private def backLink(mode: Mode)(implicit request: DataRequest[?]): Call = {
    val maybeCountryCode = request.userAnswers.get(pages.RefundingCountryPage).orElse {
      request.userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
        stored.split(",", 2).headOption.getOrElse(stored)
      }
    }
    maybeCountryCode match {
      case Some(code) if configCurrencyMapping.requiresCurrencySelection(code) =>
        controllers.routes.RefundingCurrencyController.onPageLoad(mode)
      case Some(code) =>
        val langs = configLanguageMapping.languagesFor(code)
        if (langs.size <= 1) controllers.routes.RefundingCountryController.onPageLoad(mode)
        else controllers.routes.RefundingLanguageController.onPageLoad(mode)
      case None => controllers.routes.RefundingLanguageController.onPageLoad(mode)
    }
  }
}
