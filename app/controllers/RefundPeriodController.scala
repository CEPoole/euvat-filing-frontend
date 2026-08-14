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
import models.{Mode, RefundPeriod, UserAnswers}
import navigation.Navigator
import pages.*
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.*
import play.api.{Configuration, Logging}
import queries.TraderKnownFactsQuery
import repositories.SessionRepository
import services.EuVatRefundsService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{ConfigCurrencyMapping, ConfigLanguageMapping, CountryCode}
import views.html.RefundPeriodView

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, YearMonth}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

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

  private def backLink(mode: Mode)(implicit request: DataRequest[?]): Call = {
    CountryCode.findCountryCode(request.userAnswers) match {
      case Some(code) =>
        if (configLanguageMapping.languagesFor(code).size <= 1) {
          controllers.routes.RefundingCountryController.onPageLoad(mode)
        } else {
          controllers.routes.RefundingLanguageController.onPageLoad(mode)
        }
      case None => controllers.routes.RefundingLanguageController.onPageLoad(mode)
    }
  }

  private def errorMessage(form: Form[RefundPeriodData], keys: Seq[String])(implicit messages: Messages): Option[String] = {
    val errors = form.errors.filter(e => keys.contains(e.key))
    if (errors.isEmpty) {
      None
    } else {
      Some(errors.map(e => messages(e.message, e.args*)).mkString("<br>"))
    }
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

  private def renderError(form: Form[RefundPeriodData], mode: Mode, isExempt: Boolean)(implicit
    request: DataRequest[AnyContent],
    messages: Messages
  ): Future[Result] = {
    val (mappedForm, highlighted) = formProvider.withMappedErrors(form, suppressCutoff = isExempt)
    val startMsg = errorMessage(mappedForm, Seq("start", "start.month", "start.year"))
    val endMsg = errorMessage(mappedForm, Seq("end", "end.month", "end.year"))

    Future.successful(BadRequest(view(mappedForm, mode, backLink(mode), startMsg, endMsg, highlighted, errorLinkOverrides(mappedForm))))
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

  private def isChanged(userAnswers: UserAnswers, startDate: LocalDateTime, endDate: LocalDateTime) = {
    userAnswers.get(RefundPeriodPage) match {
      case Some(existing) => existing.startDate != startDate || existing.endDate != endDate
      case None           => true
    }
  }

  private def saveAndRedirect(
    traderResponse: TraderKnownFactsResponse,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    mode: Mode
  )(using request: DataRequest[?], ec: ExecutionContext): Future[Result] = {
    val refundPeriod = RefundPeriod(startDate, endDate)

    for {
      updatedAnswer1 <- Future.fromTry(request.userAnswers.set(TraderKnownFactsQuery, traderResponse))
      updatedAnswer2 <- Future.fromTry(updatedAnswer1.set(RefundPeriodPage, refundPeriod))
      updatedAnswer3 <- Future.fromTry(updatedAnswer2.remove(CountryChangedPage))
      updatedAnswer4 <-
        if (isChanged(updatedAnswer3, startDate, endDate) && updatedAnswer3.get(ClaimDetailsCompletedPage).contains(true)) {
          Future.fromTry(updatedAnswer3.set(ClaimDetailsAmendedPage, true))
        } else {
          Future.successful(updatedAnswer3)
        }
      _ <- sessionRepository.set(updatedAnswer4)
    } yield Redirect(navigator.nextPage(RefundPeriodPage, mode, updatedAnswer4))
  }

  private def checkOverlappingPeriod(
    vrn: String,
    traderResponse: TraderKnownFactsResponse,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    mode: Mode
  )(using request: DataRequest[?], ec: ExecutionContext): Future[Result] = {
    if (endDate.getMonthValue == 12) {
      saveAndRedirect(traderResponse, startDate, endDate, mode)
    } else {
      val refundingCountry = CountryCode.findCountryCode(request.userAnswers)
      val latestApplicationRequest = LatestApplicationRequest(
        applicantVatRegNumber = vrn,
        refundingCountry      = refundingCountry,
        startDate             = Some(startDate),
        endDate               = Some(endDate)
      )
      euVatRefundsService.getLatestApplications(latestApplicationRequest).flatMap { response =>
        if (response.totalApplication > 0) {
          val refundPeriod = RefundPeriod(startDate, endDate)
          for {
            updatedAnswer1 <- Future.fromTry(request.userAnswers.set(TraderKnownFactsQuery, traderResponse))
            updatedAnswer2 <- Future.fromTry(updatedAnswer1.set(RefundPeriodPage, refundPeriod))
            updatedAnswer3 <-
              if (isChanged(request.userAnswers, startDate, endDate) && updatedAnswer2.get(ClaimDetailsCompletedPage).contains(true)) {
                Future.fromTry(updatedAnswer2.set(ClaimDetailsAmendedPage, true))
              } else {
                Future.successful(updatedAnswer2)
              }
            _ <- sessionRepository.set(updatedAnswer3)
          } yield Redirect(controllers.routes.PeriodOverlapWarningController.onPageLoad(mode))
        } else {
          logger.info(s"F5 overlap check: no overlapping applications found, startDate=$startDate, endDate=$endDate")
          saveAndRedirect(traderResponse, startDate, endDate, mode)
        }
      }
    }
  }

  private def earliestDateValidation(value: RefundPeriodData,
                                     earliest: Option[YearMonth],
                                     form: Form[RefundPeriodData]
                                    ): Option[Form[RefundPeriodData]] = {
    earliest.flatMap { min =>
      val startBefore = value.start.isBefore(min)
      val endBefore = value.end.isBefore(min)
      val filledForm = form.fill(value)
      if (!startBefore && !endBefore) {
        None
      } else {
        val human = min.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        Some {
          (startBefore, endBefore) match {
            case (true, true) =>
              filledForm
                .withError("start", "refundPeriod.error.beforeEarliest.both", human)
                .withError("end", "refundPeriod.error.beforeEarliest.both", human)
            case (true, false) => filledForm.withError("start", "refundPeriod.error.beforeEarliest.start", human)
            case (false, true) => filledForm.withError("end", "refundPeriod.error.beforeEarliest.end", human)
            case _             => filledForm
          }
        }
      }
    }
  }

  private def latestDateValidation(value: RefundPeriodData,
                                   latest: Option[YearMonth],
                                   form: Form[RefundPeriodData]
                                  ): Option[Form[RefundPeriodData]] = {
    latest.flatMap { max =>
      val startAfter = value.start.isAfter(max)
      val endAfter = value.end.isAfter(max)
      if (!startAfter && !endAfter) {
        None
      } else {
        val human = max.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        val filledForm = form.fill(value)
        Some {
          (startAfter, endAfter) match {
            case (true, true) =>
              filledForm
                .withError("start", "refundPeriod.error.afterLatest.both", human)
                .withError("end", "refundPeriod.error.afterLatest.both", human)
            case (true, false) => filledForm.withError("start", "refundPeriod.error.afterLatest.start", human)
            case (false, true) => filledForm.withError("end", "refundPeriod.error.afterLatest.end", human)
            case _             => filledForm
          }
        }
      }
    }
  }

  private def vatDateValidation(
    value: RefundPeriodData,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    trader: TraderKnownFactsResponse,
    baseForm: Form[RefundPeriodData]
  ): Option[Form[RefundPeriodData]] = {
    (trader.dateOfRegistration, trader.dateOfDeregistration) match {
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
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData)
    .async { implicit request =>
      euVatRefundsService
        .retrieveTraderKnownFacts()
        .flatMap { traderResponse =>
          val vrn = request.identifierValue.getOrElse(throw new IllegalStateException("Missing Vat registration number"))
          val (earliestForTrader, latestForTrader, isExemptForTrader) = computeEarliestAndLatest(request, Some(vrn))
          val baseForm = formProvider(earliestForTrader, latestForTrader, isExemptForTrader)

          baseForm
            .bindFromRequest()
            .fold(
              formWithErrors => renderError(formWithErrors, mode, isExemptForTrader),
              value =>
                val startDate = YearMonth.of(value.start.getYear, value.start.getMonthValue).atDay(1).atStartOfDay()
                val endDate = YearMonth.of(value.end.getYear, value.end.getMonthValue).atEndOfMonth().atTime(23, 59, 59, 999000000)

                val validationResult = earliestDateValidation(value, earliestForTrader, baseForm)
                  .orElse(latestDateValidation(value, latestForTrader, baseForm))
                  .orElse(vatDateValidation(value, startDate, endDate, traderResponse, baseForm))

                validationResult match {
                  case None                => checkOverlappingPeriod(vrn, traderResponse, startDate, endDate, mode)
                  case Some(formWithError) => renderError(formWithError, mode, isExemptForTrader)
                }
            )
        }
        .recover { case NonFatal(e) =>
          logger.error("Failed to retrieve data from backend", e)
          Redirect(routes.JourneyRecoveryController.onPageLoad())
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

    val traderVrnOpt = traderVrnOverride.orElse(request.identifierValue)
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

}
