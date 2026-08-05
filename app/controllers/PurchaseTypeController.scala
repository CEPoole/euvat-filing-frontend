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
import models.requests.DataRequest
import forms.PurchaseTypeFormProvider
import models.{Mode, PurchaseType}
import navigation.Navigator
import pages.{PurchaseTypePage, CountryChangedPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents, RequestHeader}
import repositories.SessionRepository
import utils.MountPrefix
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
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
  view: PurchaseTypeView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

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

      Future.fromTry(clearedTry).flatMap(updated => sessionRepository.set(updated).map(_ => {
        val preparedForm = updated.get(PurchaseTypePage).fold(form)(form.fill)
        Ok(view(preparedForm, mode, backLink(mode)))
      }))
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
        value =>
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
          } yield {
            val call = navigator.nextPage(PurchaseTypePage, mode, updatedAnswers)
            val prefix = MountPrefix.get
            if (prefix.isEmpty || call.url.startsWith(prefix)) Redirect(call)
            else Redirect(Call(call.method, s"$prefix${call.url}"))
          }
      )
  }

  // mount prefix is provided by utils.MountPrefix
}
