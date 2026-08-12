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
import forms.PurchaseTypeFormProvider
import models.requests.{AddPurchaseRequest, DataRequest}
import models.{Mode, PurchaseType, PurchaseTypeCode, UserAnswers}
import navigation.Navigator
import pages.{AddPurchaseResponsePage, PurchaseTypePage}
import play.api.Logging
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.*
import queries.ClaimApplicationResponseQuery
import repositories.SessionRepository
import services.EuVatRefundsService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import utils.MountPrefix
import views.html.PurchaseTypeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PurchaseTypeController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: PurchaseTypeFormProvider,
  val controllerComponents: MessagesControllerComponents,
  euVatRefundsService: EuVatRefundsService,
  view: PurchaseTypeView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with Logging {

  val form: Form[PurchaseType] = formProvider()

  private def backLink(mode: Mode)(implicit request: DataRequest[?]) =
    routes.BeforeYouStartPurchaseController.onPageLoad()

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // If the country was changed, clear the whole purchase chain
    if (request.userAnswers.get(pages.CountryChangedPage).contains(true)) {
      // Clear the full purchase chain when the user changed the country so
      // subsequent steps are re-evaluated for the new country.
      val clearedTry = for {
        afterRemovedPurchaseType        <- request.userAnswers.remove(pages.PurchaseTypePage)
        afterRemovedPurchaseSubType     <- afterRemovedPurchaseType.remove(pages.PurchaseSubTypePage)
        afterRemovedPurchaseSubTypeLbl  <- afterRemovedPurchaseSubType.remove(pages.PurchaseSubTypeLabelPage)
        afterRemovedPurchaseSubCategory <- afterRemovedPurchaseSubTypeLbl.remove(pages.PurchaseSubCategoryPage)
        afterRemovedPurchaseSubCatLbl   <- afterRemovedPurchaseSubCategory.remove(pages.PurchaseSubCategoryLabelPage)
        afterClearedFlag                <- afterRemovedPurchaseSubCatLbl.remove(pages.CountryChangedPage)
      } yield afterClearedFlag

      Future
        .fromTry(clearedTry)
        .flatMap(updated =>
          sessionRepository
            .set(updated)
            .map(_ => {
              val preparedForm = updated.get(PurchaseTypePage).fold(form)(form.fill)
              Ok(view(preparedForm, mode, backLink(mode)))
            })
        )
    } else {
      val preparedForm = request.userAnswers.get(PurchaseTypePage).fold(form)(form.fill)
      Future.successful(Ok(view(preparedForm, mode, backLink(mode))))
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(view(formWithErrors, mode, backLink(mode)))),
        value => {
          val saved = request.userAnswers.get(PurchaseTypePage) match {
            case Some(prev) if prev != value =>
              for {
                afterRemovedSubType        <- request.userAnswers.remove(pages.PurchaseSubTypePage)
                afterRemovedSubTypeLabel   <- afterRemovedSubType.remove(pages.PurchaseSubTypeLabelPage)
                afterRemovedSubCategory    <- afterRemovedSubTypeLabel.remove(pages.PurchaseSubCategoryPage)
                afterRemovedSubCategoryLbl <- afterRemovedSubCategory.remove(pages.PurchaseSubCategoryLabelPage)
                afterRemovedDescribe       <- afterRemovedSubCategoryLbl.remove(pages.DescribeItemsOnInvoicePage)
                afterSetPurchaseType       <- afterRemovedDescribe.set(PurchaseTypePage, value)
              } yield afterSetPurchaseType
            case _ => request.userAnswers.set(PurchaseTypePage, value)
          }

          for {
            updatedAnswers <- Future.fromTry(saved)
            _              <- sessionRepository.set(updatedAnswers)
            result         <- addPurchaseAndPersist(updatedAnswers, value, mode)
          } yield result
        }
      )
  }

  private def addPurchaseAndPersist(
    answers: UserAnswers,
    purchaseType: PurchaseType,
    mode: Mode
  )(implicit request: DataRequest[?]): Future[Result] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    answers
      .get(ClaimApplicationResponseQuery)
      .fold {
        logger.warn("Missing applicationId for addPurchase")
        Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
      } { claimResponse =>

        val purchaseRequest = AddPurchaseRequest(
          applicationId              = claimResponse.applicationId.toLong,
          goodsDescriptionCategory   = PurchaseTypeCode.codeFor(purchaseType),
          goodsDescriptionText       = None,
          purchaseSubcategory        = None,
          simplifiedInvoiceIndicator = None,
          supplierName               = None,
          supplierAddress1           = None,
          supplierAddress2           = None,
          supplierAddress3           = None,
          supplierVatRegNumber       = None,
          supplierTaxIdentifier      = None,
          invoiceDate                = None,
          invoiceNumber              = None,
          currencyCode               = None,
          taxableAmount              = None,
          vatAmount                  = None,
          deductibleVatAmount        = None,
          updateSequenceNumber       = claimResponse.updateSeqNumber
        )

        euVatRefundsService
          .addPurchase(purchaseRequest)
          .flatMap { response =>
            for {
              updatedAnswers <- Future.fromTry(
                                  answers.set(AddPurchaseResponsePage, response)
                                )
              _ <- sessionRepository.set(updatedAnswers)
            } yield {
              val call = navigator.nextPage(PurchaseTypePage, mode, updatedAnswers)
              val prefix = MountPrefix.get

              if (prefix.isEmpty || call.url.startsWith(prefix)) {
                Redirect(call)
              } else {
                Redirect(Call(call.method, s"$prefix${call.url}"))
              }
            }
          }
          .recover { case ex =>
            logger.error("Error while adding the purchase", ex)
            Redirect(routes.JourneyRecoveryController.onPageLoad())
          }
      }
  }
  // mount prefix is provided by utils.MountPrefix
}
