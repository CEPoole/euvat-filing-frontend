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

import com.google.inject.Inject
import controllers.actions.{DataRequiredAction, DataRetrievalAction, IdentifierAction}
import models.UserAnswers
import models.requests.ApplicationRequest
import models.responses.ApplicationResponse
import pages.*
import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import models.requests.LatestApplicationRequest
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import services.EuVatRefundsService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{ConfigCurrencyMapping, ConfigLanguageMapping}
import viewmodels.checkAnswers.CheckYourClaimDetailsSummary
import views.html.CheckYourClaimDetailsView

import scala.concurrent.{ExecutionContext, Future}

class CheckYourClaimDetailsController @Inject() (
  override val messagesApi: MessagesApi,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  val controllerComponents: MessagesControllerComponents,
  view: CheckYourClaimDetailsView,
  configLanguageMapping: ConfigLanguageMapping,
  configCurrencyMapping: ConfigCurrencyMapping,
  sessionRepository: SessionRepository,
  service: EuVatRefundsService
)(using ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with Logging {

  def onPageLoad(): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val summaryList = buildSummaryList(request.userAnswers)
    val isPostSubmission = request.userAnswers.get(pages.ClaimDetailsCompletedPage).contains(true)
    val isAmended = request.userAnswers.get(pages.ClaimDetailsAmendedPage).contains(true)
    Ok(view(summaryList, isPostSubmission, isAmended))
  }

  def onSubmit(): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val isPostSubmission = request.userAnswers.get(pages.ClaimDetailsCompletedPage).contains(true)

    (
      for {
        flaggedAnswers <- Future.fromTry {
                            if (!isPostSubmission) {
                              request.userAnswers.set(ClaimDetailsCompletedPage, true)
                            } else {
                              request.userAnswers.remove(pages.ClaimDetailsAmendedPage)
                            }
                          }
        appRequest     <- buildAppRequest(flaggedAnswers)
        result         <- service.retrieveTraderKnownFacts().flatMap { traderFacts =>
                            implicit val hc = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
                            val latestReq = LatestApplicationRequest(
                              applicantVatRegNumber = traderFacts.vatRegNumber.toString,
                              refundingCountry = flaggedAnswers.get(pages.RefundingCountryPage),
                              startDate = None,
                              endDate = None,
                              representativeId = None,
                              maxNumber = 10000,
                              orderBy = Some(0),
                              sortOrder = Some("DESC"),
                              startAt = Some(0)
                            )

                            service.getLatestApplications(latestReq).flatMap { latestResp =>
                              val isDuplicate = latestResp.applications.exists { app =>
                                val statusIsD = app.applicationStatus.exists(_.equalsIgnoreCase("D"))
                                val submissionIsNull = app.submissionStatus.isEmpty
                                statusIsD || submissionIsNull
                              }

                              if (isDuplicate) Future.successful(Redirect(controllers.routes.JourneyRecoveryController.onPageLoad()))
                              else {
                                for {
                                  claimResponse  <- service.createApplication(appRequest)
                                  updatedAnswers <- Future.fromTry(flaggedAnswers.set(ClaimApplicationResponsePage, claimResponse))
                                  _              <- sessionRepository.set(updatedAnswers)
                                } yield {
                                  if (claimResponse.applicationId > 0) Redirect(controllers.routes.TaskListDashboardController.onPageLoad())
                                  else Redirect(controllers.routes.JourneyRecoveryController.onPageLoad())
                                }
                              }
                            }
                          }
      } yield result
    )
      .recover { case ex: Exception =>
        logger.error("Error while saving the refund application", ex)
        Redirect(controllers.routes.JourneyRecoveryController.onPageLoad())
      }
  }

  private def buildSummaryList(
    answers: UserAnswers
  )(implicit messages: Messages): Seq[(String, Seq[(String, Option[String], Seq[(String, String, String)])])] = {
    val maybeCountryCode = answers.get(pages.RefundingCountryPage).orElse {
      answers.get(pages.RefundingCountryNamePage).map { stored =>
        stored.split(",", 2).headOption.getOrElse(stored)
      }
    }

    val maybeCurrencyDisplayName: Option[String] =
      answers.get(pages.RefundingCurrencyPage).map { code =>
        maybeCountryCode.toSeq
          .flatMap(configCurrencyMapping.currenciesFor)
          .find(_._2 == code)
          .map(c => messages(s"refundingCurrency.${c._1}", c._3))
          .getOrElse(code)
      }

    val languageSection: Seq[(String, Seq[(String, Option[String], Seq[(String, String, String)])])] =
      maybeCountryCode match {
        case Some(code) if configLanguageMapping.languagesFor(code).size > 1 =>
          Seq(("checkYourClaimDetails.refundingLanguage.label", Seq(CheckYourClaimDetailsSummary.rowLanguage(answers)).flatten))
        case _ => Seq.empty
      }

    val currencySection: Seq[(String, Seq[(String, Option[String], Seq[(String, String, String)])])] =
      maybeCountryCode match {
        case Some(code) if configCurrencyMapping.requiresCurrencySelection(code) =>
          Seq(("checkYourClaimDetails.refundingCurrency.label", Seq(CheckYourClaimDetailsSummary.rowCurrency(maybeCurrencyDisplayName)).flatten))
        case _ => Seq.empty
      }

    Seq(("checkYourClaimDetails.refundingCountry.label", Seq(CheckYourClaimDetailsSummary.rowCountry(answers)).flatten)) ++
      languageSection ++
      currencySection ++
      Seq(
        ("checkYourClaimDetails.refundingPeriod.label",
         Seq(CheckYourClaimDetailsSummary.rowRefundStart(answers), CheckYourClaimDetailsSummary.rowRefundEnd(answers)).flatten
        ),
        ("checkYourClaimDetails.contactDetails.label",
         Seq(CheckYourClaimDetailsSummary.rowContactEmail(answers), CheckYourClaimDetailsSummary.rowContactPhone(answers)).flatten
        ),
        ("checkYourClaimDetails.businessActivity.label",
         Seq(
           CheckYourClaimDetailsSummary.rowBusinessActivity(answers),
           CheckYourClaimDetailsSummary.rowBusinessActivity2(answers),
           CheckYourClaimDetailsSummary.rowBusinessActivity3(answers)
         ).flatten
        )
      )
  }

  private def buildAppRequest(userAnswers: UserAnswers): Future[ApplicationRequest] = {
    val countryCode = userAnswers
      .get(RefundingCountryPage)
      .getOrElse(throw new RuntimeException("Country code missing"))
    val currencyCode = userAnswers
      .get(RefundingCurrencyPage)
      .getOrElse(throw new RuntimeException("Currency code missing"))
    val languageCode = userAnswers
      .get(RefundingLanguagePage)
      .map(_.code)
      .getOrElse(throw new RuntimeException("Language code missing"))
    val refundStartDate = userAnswers
      .get(RefundPeriodPage)
      .map(_.startDate)
      .getOrElse(throw new RuntimeException("RefundPeriodPage startDate missing"))
    val refundEndDate = userAnswers
      .get(RefundPeriodPage)
      .map(_.endDate)
      .getOrElse(throw new RuntimeException("RefundPeriodPage endDate missing"))
    val email = userAnswers
      .get(ContactDetailsPage)
      .map(_.email)
      .getOrElse(throw new RuntimeException("Email contact detail missing"))
    val telephone = userAnswers
      .get(ContactDetailsPage)
      .map(_.telephone)
      .getOrElse(throw new RuntimeException("Telephone contact detail missing"))
    val businessActivityCode1 = userAnswers
      .get(BusinessActivityCodePage)
      .getOrElse(throw new RuntimeException("Business activity code missing"))
    val businessActivityCode2 = userAnswers.get(BusinessActivityCodeTwoPage).getOrElse("")
    val businessActivityCode3 = userAnswers.get(BusinessActivityCodeThreePage).getOrElse("")

    Future.successful(
      ApplicationRequest(
        applicationLanguage      = Some(languageCode),
        applicantEmailAddress    = Some(email),
        applicantTelephoneNumber = Some(telephone).value,
        refundingCountryCode     = Some(countryCode),
        periodStartDate          = Some(refundStartDate),
        periodEndDate            = Some(refundEndDate),
        businessActivityCode1    = Some(businessActivityCode1),
        businessActivityCode2    = Some(businessActivityCode2),
        businessActivityCode3    = Some(businessActivityCode3)
      )
    )
  }

}
