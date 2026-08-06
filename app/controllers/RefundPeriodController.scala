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
import play.api.libs.json.Json
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.Configuration
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
  configuration: Configuration,
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
    val (earliestOpt, latestOpt, isExempt) = computeEarliestAndLatest(request)

    val preparedForm = request.userAnswers.get(RefundPeriodPage) match {
      case None => formProvider(earliestOpt, latestOpt, isExempt)
      case Some(value) =>
        val start = java.time.YearMonth.of(value.startDate.getYear, value.startDate.getMonthValue)
        val end = java.time.YearMonth.of(value.endDate.getYear, value.endDate.getMonthValue)
        formProvider(earliestOpt, latestOpt, isExempt).fill(RefundPeriodData(start, end))
    }
    val (mappedForm, highlighted) = formProvider.withMappedErrors(preparedForm, suppressCutoff = isExempt)
    val startMsg = errorMessage(mappedForm, Seq("start", "start.month", "start.year"))
    val endMsg = errorMessage(mappedForm, Seq("end", "end.month", "end.year"))
    Ok(view(mappedForm, mode, backLink(mode), startMsg, endMsg, highlighted, errorLinkOverrides(mappedForm)))
  }

  private def renderError(form: Form[RefundPeriodData], mode: Mode, traderVrnOverride: Option[String] = None)(implicit
    request: DataRequest[AnyContent],
    messages: Messages
  ) = {
    val (_, _, isExempt) = computeEarliestAndLatest(request, traderVrnOverride)
    val (mappedForm, highlighted) = formProvider.withMappedErrors(form, suppressCutoff = isExempt)
    val startMsg = errorMessage(mappedForm, Seq("start", "start.month", "start.year"))
    val endMsg = errorMessage(mappedForm, Seq("end", "end.month", "end.year"))

    logger.debug(s"RefundPeriodController.renderError: form.errors=${form.errors.map(e => s"${e.key}->${e.message}").mkString(",")}")

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
      // valid when start is within three months before the registration date or anytime after
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
      updatedAnswer4 <- if (isChanged && updatedAnswer3.get(pages.ClaimDetailsCompletedPage).contains(true)) {
                          Future.fromTry(updatedAnswer3.set(pages.ClaimDetailsAmendedPage, true))
                        } else {
                          Future.successful(updatedAnswer3)
                        }
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
          val refundPeriod = RefundPeriod(startDate, endDate)
          for {
            updatedAnswer1 <- Future.fromTry(request.userAnswers.set(TraderKnownFactsQuery, traderResponse))
            updatedAnswer2 <- Future.fromTry(updatedAnswer1.set(RefundPeriodPage, refundPeriod))
            _              <- sessionRepository.set(updatedAnswer2)
          } yield Redirect(controllers.routes.PeriodOverlapWarningController.onPageLoad(mode))
        } else {
          logger.info(s"F5 overlap check: no overlapping applications found, startDate=$startDate, endDate=$endDate")
          saveAndRedirect(traderResponse, startDate, endDate, mode)
        }
      }
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val traderInAnswers = request.userAnswers.get(TraderKnownFactsQuery)

    def processWithTrader(traderResponse: TraderKnownFactsResponse): Future[Result] = {
      val (earliestForTrader, latestForTrader, isExemptForTrader) = computeEarliestAndLatest(request, Some(traderResponse.vatRegNumber.toString))
      val baseForm = formProvider(earliestForTrader, latestForTrader, isExemptForTrader)

      baseForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            // Do not override cutoff errors with VAT-registration checks here; surface
            // the binding/form errors directly so validation precedence remains
            // earliest/latest -> field-level -> VAT-registration (F6) -> overlap.
            renderError(formWithErrors, mode, Some(traderResponse.vatRegNumber.toString))
          },
          value => {
            val startDate = YearMonth.of(value.start.getYear, value.start.getMonthValue).atDay(1).atStartOfDay()
            val endDate = YearMonth.of(value.end.getYear, value.end.getMonthValue).atEndOfMonth().atTime(23, 59, 59, 999000000)

            // earliest/latest re-check (defensive) and business checks
            earliestForTrader match {
              case Some(min) if value.start.isBefore(min) || value.end.isBefore(min) =>
                val startBefore = value.start.isBefore(min)
                val endBefore = value.end.isBefore(min)
                val human = min.atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
                var f = baseForm.fill(value)
                if (startBefore && endBefore) {
                  f = f
                    .withError("start", "refundPeriod.error.beforeEarliest.both", human)
                    .withError("end", "refundPeriod.error.beforeEarliest.both", human)
                } else if (startBefore) {
                  f = f.withError("start", "refundPeriod.error.beforeEarliest.start", human)
                } else if (endBefore) {
                  f = f.withError("end", "refundPeriod.error.beforeEarliest.end", human)
                }
                val formWithError = f
                renderError(formWithError, mode, Some(traderResponse.vatRegNumber.toString))
              case _ =>
                latestForTrader match {
                  case Some(max) if value.start.isAfter(max) || value.end.isAfter(max) =>
                    val startAfter = value.start.isAfter(max)
                    val endAfter = value.end.isAfter(max)
                    val human = max.atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
                    var f2 = baseForm.fill(value)
                    if (startAfter && endAfter) {
                      f2 = f2
                        .withError("start", "refundPeriod.error.afterLatest.both", human)
                        .withError("end", "refundPeriod.error.afterLatest.both", human)
                    } else if (startAfter) {
                      f2 = f2.withError("start", "refundPeriod.error.afterLatest.start", human)
                    } else if (endAfter) {
                      f2 = f2.withError("end", "refundPeriod.error.afterLatest.end", human)
                    }
                    val formWithError = f2
                    renderError(formWithError, mode, Some(traderResponse.vatRegNumber.toString))
                  case _ =>
                    val maybeErrorForm: Option[Form[RefundPeriodData]] =
                      (traderResponse.dateOfRegistration, traderResponse.dateOfDeregistration) match {
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
                        case _ => None
                      }

                    maybeErrorForm match {
                      case Some(formWithError) => renderError(formWithError, mode, Some(traderResponse.vatRegNumber.toString))
                      case None                => checkOverlappingPeriod(traderResponse, startDate, endDate, mode)
                    }
                }
            }
          }
        )
    }

    traderInAnswers match {
      case Some(trader) => processWithTrader(trader)
      case None         => euVatRefundsService.retrieveTraderKnownFacts().flatMap(processWithTrader)
    }
  }

  private def computeEarliestAndLatest(request: DataRequest[?],
                                       traderVrnOverride: Option[String] = None
                                      ): (Option[YearMonth], Option[YearMonth], Boolean) = {
    def parseMMYY(s: String): Option[YearMonth] = {
      val parts = s.split("/")
      if (parts.length == 2 && parts(0).forall(_.isDigit) && parts(1).forall(_.isDigit) && parts(0).length == 2 && parts(1).length == 2) {
        try {
          val month = parts(0).toInt
          val yearTwo = parts(1).toInt
          val year = 2000 + yearTwo
          Some(YearMonth.of(year, month))
        } catch {
          case _: Throwable => None
        }
      } else None
    }

    val traderVrnOpt = traderVrnOverride.orElse(request.userAnswers.get(TraderKnownFactsQuery).map(_.vatRegNumber.toString))

    val canCreate = configuration.getOptional[String]("settings.refund.can.create.vrns").map(_.split(",").map(_.trim).toSet).getOrElse(Set.empty)
    val canAmend = configuration.getOptional[String]("settings.refund.can.amend.vrns").map(_.split(",").map(_.trim).toSet).getOrElse(Set.empty)

    val exemptSet = canCreate ++ canAmend

    val isExempt = traderVrnOpt.exists(exemptSet.contains)

    val earliest: Option[YearMonth] = if (isExempt) {
      Some(YearMonth.of(2020, 1))
    } else {
      // If config is missing or blank => default to January 2021 per spec.
      // If config exists but fails to parse, skip earliest validation (None).
      configuration.getOptional[String]("settings.refund.start.date.earliest.permitted") match {
        case None                      => Some(YearMonth.of(2021, 1))
        case Some(v) if v.trim.isEmpty => Some(YearMonth.of(2021, 1))
        case Some(v)                   => parseMMYY(v) // if parse fails -> None => skip validation
      }
    }

    val latest: Option[YearMonth] = if (isExempt) {
      configuration.getOptional[String]("settings.refund.start.date.latest.permitted").flatMap(s => if (s.trim.isEmpty) None else parseMMYY(s))
    } else None

    (earliest, latest, isExempt)
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
