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
import models.requests.{ApplicationRequest, LatestApplicationRequest}
import models.responses.ApplicationResponse
import pages.*
import play.api.Logging
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.*
import queries.{ClaimApplicationResponseQuery, LatestCountryResponseQuery}
import repositories.SessionRepository
import services.EuVatRefundsService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{ConfigCurrencyMapping, ConfigLanguageMapping, CountryCode}
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
    val isPostSubmission = request.userAnswers.get(ClaimDetailsCompletedPage).contains(true)
    val isAmended = request.userAnswers.get(ClaimDetailsAmendedPage).contains(true)
    Ok(view(summaryList, isPostSubmission, isAmended))
  }

  def onSubmit(): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val userAnswers = request.userAnswers
    val isPostSubmission = userAnswers.get(ClaimDetailsCompletedPage).contains(true)
    val isAmended = userAnswers.get(ClaimDetailsAmendedPage).contains(true)

    if (isPostSubmission && !isAmended) {
      Future.successful(Redirect(routes.TaskListDashboardController.onPageLoad()))
    } else {
      val updatedAnswers = Future.fromTry {
        if (isPostSubmission) {
          userAnswers.remove(ClaimDetailsAmendedPage)
        } else {
          userAnswers.set(ClaimDetailsCompletedPage, true)
        }
      }

      updatedAnswers
        .flatMap { flaggedAnswers =>
          flaggedAnswers.get(LatestCountryResponseQuery) match {
            case Some(latestResp) if !isPostSubmission && latestResp.totalApplication > 0 =>
              logger.warn("You cannot have more than one draft claim for each EU member state")
              Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
            case _ =>
              val claimRequest = buildClaimRequest(flaggedAnswers)
              saveClaimResponseAndRedirect(flaggedAnswers, claimRequest)
          }
        }
        .recover { case ex =>
          logger.error("Error while saving the refund application", ex)
          Redirect(routes.JourneyRecoveryController.onPageLoad())
        }
    }
  }

  private def saveClaimResponseAndRedirect(flaggedAnswers: UserAnswers, appRequest: ApplicationRequest)(using RequestHeader): Future[Result] = {
    for {
      claimResponse  <- service.createApplication(appRequest)
      updatedAnswers <- Future.fromTry(flaggedAnswers.set(ClaimApplicationResponseQuery, claimResponse))
      _              <- sessionRepository.set(updatedAnswers)
    } yield {
      if (claimResponse.applicationId > 0) {
        Redirect(controllers.routes.TaskListDashboardController.onPageLoad())
      } else {
        Redirect(controllers.routes.JourneyRecoveryController.onPageLoad())
      }
    }
  }

  private def buildSummaryList(
    answers: UserAnswers
  )(implicit messages: Messages): Seq[(String, Seq[(String, Option[String], Seq[(String, String, String)])])] = {
    val languageSection: Seq[(String, Seq[(String, Option[String], Seq[(String, String, String)])])] =
      CountryCode.findCountryCode(answers) match {
        case Some(code) if configLanguageMapping.languagesFor(code).size > 1 =>
          Seq(("checkYourClaimDetails.refundingLanguage.label", Seq(CheckYourClaimDetailsSummary.rowLanguage(answers)).flatten))
        case _ => Seq.empty
      }

    Seq(("checkYourClaimDetails.refundingCountry.label", Seq(CheckYourClaimDetailsSummary.rowCountry(answers)).flatten)) ++
      languageSection ++
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

  private def buildClaimRequest(userAnswers: UserAnswers): ApplicationRequest = {
    val countryCode = userAnswers.get(RefundingCountryPage).getOrElse(throw new RuntimeException("Country code missing"))
    val languageCode = userAnswers.get(RefundingLanguagePage).map(_.code).getOrElse(throw new RuntimeException("Language code missing"))
    val refundPeriod = userAnswers.get(RefundPeriodPage).getOrElse(throw new RuntimeException("Refund period missing"))
    val contactDetails = userAnswers.get(ContactDetailsPage).getOrElse(throw new RuntimeException("Contact details missing"))
    val businessActivityCode1 = userAnswers.get(BusinessActivityCodePage).getOrElse(throw new RuntimeException("Business activity code missing"))

    ApplicationRequest(
      refundingCountryCode     = Some(countryCode),
      applicationLanguage      = Some(languageCode),
      applicantEmailAddress    = Some(contactDetails.email),
      applicantTelephoneNumber = Some(contactDetails.telephone.getOrElse("")),
      periodStartDate          = Some(refundPeriod.startDate),
      periodEndDate            = Some(refundPeriod.endDate),
      businessActivityCode1    = Some(businessActivityCode1),
      businessActivityCode2    = userAnswers.get(BusinessActivityCodeTwoPage),
      businessActivityCode3    = userAnswers.get(BusinessActivityCodeThreePage)
    )
  }

}
