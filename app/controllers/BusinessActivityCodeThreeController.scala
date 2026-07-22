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
import forms.BusinessActivityCodeThreeFormProvider
import models.{NormalMode, UserAnswers}
import models.Mode
import models.CheckMode
import navigation.Navigator
import pages.{BusinessActivityCodePage, BusinessActivityCodeThreePage, BusinessActivityCodeTwoPage, ClaimDetailsAmendedPage, ClaimDetailsCompletedPage}
import play.api.Configuration
import play.api.data.FormError
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.BusinessActivityCodeThreeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logger

import scala.util.control.NonFatal

class BusinessActivityCodeThreeController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: BusinessActivityCodeThreeFormProvider,
  config: Configuration,
  val controllerComponents: MessagesControllerComponents,
  view: BusinessActivityCodeThreeView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  private def buildListAndForm() = {
    val activities = utils.BusinessActivityList.fromConfig(config)
    val form = formProvider()
    (activities, form)
  }

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val (_, form) = buildListAndForm()
    val preparedForm = request.userAnswers.get(BusinessActivityCodeThreePage).fold(form)(form.fill)
    Ok(view(preparedForm, Some(routes.BusinessActivityTwoController.onPageLoad(mode).url), mode))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val baseAnswers: UserAnswers = request.userAnswers
    val excludeCodes = baseAnswers.get(BusinessActivityCodePage).toSeq.toSet ++ baseAnswers.get(BusinessActivityCodeTwoPage).toSeq.toSet
    val (activities, form) = buildListAndForm()

    val boundResult = form
      .bindFromRequest()
      .fold(
        formWithErrors => {
          val typed = request.body.asFormUrlEncoded.flatMap(_.get("valueTyped").flatMap(_.headOption)).getOrElse("")
          val submitted = request.body.asFormUrlEncoded.flatMap(_.get("value").flatMap(_.headOption)).getOrElse("")
          // if the submitted value is one of the excluded codes, replace the error with a duplicate-specific message
          val duplicateFrom =
            if (baseAnswers.get(BusinessActivityCodeTwoPage).contains(submitted)) Some("Business activity 2")
            else if (baseAnswers.get(BusinessActivityCodePage).contains(submitted)) Some("Business activity 1")
            else None

          if (duplicateFrom.isDefined) {

            val duplicateError = FormError("value", "businessActivityCodeThree.error.duplicate", Seq(duplicateFrom.get, submitted))
            val filtered = formWithErrors.errors.filterNot(e => e.key == "value")
            val adjustedForm = formWithErrors.copy(errors = filtered :+ duplicateError)

            Future.successful(BadRequest(view(adjustedForm, Some(routes.BusinessActivityTwoController.onPageLoad(mode).url), mode)))
          } else {
            val adjustedForm = if (typed.trim.nonEmpty) {
              val filtered = formWithErrors.errors.filterNot(e => e.key == "value" && e.message == "businessActivityCodeThree.error.required")
              formWithErrors.copy(errors = filtered :+ FormError("value", "businessActivityCodeThree.error.invalid"))
            } else {
              formWithErrors
            }
            Future.successful(BadRequest(view(adjustedForm, Some(routes.BusinessActivityTwoController.onPageLoad(mode).url), mode)))
          }
        },
        value => {

          // if the submitted value duplicates BA1 or BA2, show duplicate error
          val duplicateFrom =
            if (baseAnswers.get(BusinessActivityCodeTwoPage).contains(value)) Some("Business activity 2")
            else if (baseAnswers.get(BusinessActivityCodePage).contains(value)) Some("Business activity 1")
            else None

          duplicateFrom match {
            case Some(from) =>
              val duplicateError = FormError("value", "businessActivityCodeThree.error.duplicate")
              val duplicateForm = form.fill(value).withError(duplicateError)
              Future.successful(BadRequest(view(duplicateForm, Some(routes.BusinessActivityTwoController.onPageLoad(mode).url), mode)))
            case None =>
              val isChanged = baseAnswers.get(BusinessActivityCodeThreePage) match {
                case Some(existing) => existing != value
                case None           => true
              }
              for {
                updatedAnswers <- Future.fromTry(baseAnswers.set(BusinessActivityCodeThreePage, value))
                updatedAnswers2 <- if (isChanged && updatedAnswers.get(ClaimDetailsCompletedPage).contains(true))
                                     Future.fromTry(updatedAnswers.set(ClaimDetailsAmendedPage, true))
                                   else
                                     Future.successful(updatedAnswers)
                _ <- sessionRepository.set(updatedAnswers2)
              } yield mode match {
                case CheckMode  => Redirect(routes.BusinessActivityThreeController.onPageLoad())
                case NormalMode => Redirect(navigator.nextPage(BusinessActivityCodeThreePage, mode, updatedAnswers2))
              }
          }
        }
      )

    boundResult.recover { case NonFatal(e) =>
      Logger(getClass).error("Error in BusinessActivityCodeThreeController.onSubmit", e)
      BadRequest(view(form.bindFromRequest(), Some(routes.BusinessActivityTwoController.onPageLoad(mode).url), mode))
    }
  }
}
