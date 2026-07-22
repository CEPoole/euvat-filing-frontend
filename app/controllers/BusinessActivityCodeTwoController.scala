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
import forms.BusinessActivityCodeTwoFormProvider
import models.{CheckMode, Mode, NormalMode, UserAnswers}
import navigation.Navigator
import pages.{BusinessActivityCodePage, BusinessActivityCodeThreePage, BusinessActivityCodeTwoPage, BusinessActivityThreePage, ClaimDetailsAmendedPage, ClaimDetailsCompletedPage}
import play.api.Configuration
import play.api.data.FormError
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.BusinessActivityList
import views.html.BusinessActivityCodeTwoView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logger

import scala.util.control.NonFatal

class BusinessActivityCodeTwoController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: BusinessActivityCodeTwoFormProvider,
  config: Configuration,
  val controllerComponents: MessagesControllerComponents,
  view: BusinessActivityCodeTwoView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  private def buildListAndForm() = {
    val activities = BusinessActivityList.fromConfig(config)
    val form = formProvider()
    (activities, form)
  }

  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData) { implicit request =>
    val userAnswers = request.userAnswers
    val (activities, form) = buildListAndForm()
    val keyValue = request.getQueryString("key").getOrElse("") // check if page 3 Change link clicked otherwise empty

    for {
      updatedAnswer <-
        Future.fromTry(userAnswers.set(BusinessActivityThreePage, keyValue)) // Save the click to session page (transient navigation flag)
      _ <- sessionRepository.set(updatedAnswer)
    } yield None

    val preparedForm = userAnswers.get(BusinessActivityCodeTwoPage).fold(form)(form.fill)
    Ok(view(preparedForm, Some(routes.BusinessActivityController.onPageLoad(mode).url), mode))
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val baseAnswers: UserAnswers = request.userAnswers
    val ba1 = baseAnswers.get(BusinessActivityCodePage)
    val ba2 = baseAnswers.get(BusinessActivityCodeTwoPage)
    val ba3 = baseAnswers.get(BusinessActivityCodeThreePage)
    val (activities, form) = buildListAndForm()
    val page3 = baseAnswers.get(BusinessActivityThreePage)

    val boundResult = form
      .bindFromRequest()
      .fold(
        formWithErrors => {
          val typed = request.body.asFormUrlEncoded.flatMap(_.get("valueTyped").flatMap(_.headOption)).getOrElse("")
          val submitted = request.body.asFormUrlEncoded.flatMap(_.get("value").flatMap(_.headOption)).getOrElse("")

          val duplicateFrom =
            if (ba1.contains(submitted)) Some("Business activity 1")
            else if (ba3.contains(submitted)) Some("Business activity 3")
            else if (ba2.contains(submitted)) Some("Business activity 2")
            else None

          if (duplicateFrom.isDefined && duplicateFrom.get != "Business activity 2") {
            val duplicateError = FormError("value", "businessActivityCodeTwo.error.duplicate", Seq(duplicateFrom.get, submitted))
            val filtered = formWithErrors.errors.filterNot(e => e.key == "value")
            val adjustedForm = formWithErrors.copy(errors = filtered :+ duplicateError)
            Future.successful(BadRequest(view(adjustedForm, Some(routes.BusinessActivityController.onPageLoad(mode).url), mode)))
          } else {
            val adjustedForm = if (typed.trim.nonEmpty) {
              val filtered = formWithErrors.errors.filterNot(e => e.key == "value" && e.message == "businessActivityCodeTwo.error.required")
              formWithErrors.copy(errors = filtered :+ FormError("value", "businessActivityCodeTwo.error.invalid"))
            } else {
              formWithErrors
            }
            Future.successful(BadRequest(view(adjustedForm, Some(routes.BusinessActivityController.onPageLoad(mode).url), mode)))
          }
        },
        value => {
          if  (ba1.contains(value) || ba3.contains(value)) {
            val from = if (ba3.contains(value)) "Business activity 3" else "Business activity 1"
            val duplicateForm = form.fill(value).withError("value", "businessActivityCodeTwo.error.duplicate")
            Future.successful(BadRequest(view(duplicateForm, Some(routes.BusinessActivityController.onPageLoad(mode).url), mode)))
          } else {
            val isChanged = baseAnswers.get(BusinessActivityCodeTwoPage) match {
              case Some(existing) => existing != value
              case None           => true
            }
            for {
              updatedAnswer1 <- Future.fromTry(baseAnswers.set(BusinessActivityCodeTwoPage, value))
              updatedAnswers <- Future.fromTry(updatedAnswer1.remove(BusinessActivityThreePage))
              updatedAnswers2 <- if (isChanged && updatedAnswers.get(ClaimDetailsCompletedPage).contains(true))
                                   Future.fromTry(updatedAnswers.set(ClaimDetailsAmendedPage, true))
                                 else
                                   Future.successful(updatedAnswers)
              _ <- sessionRepository.set(updatedAnswers2)
            } yield mode match {
              case models.CheckMode =>
                if (ba3.isDefined) Redirect(routes.BusinessActivityThreeController.onPageLoad())
                else Redirect(routes.BusinessActivityTwoController.onPageLoad(CheckMode))
              case models.NormalMode =>
                if (page3.contains("ba3Page")) Redirect(routes.BusinessActivityThreeController.onPageLoad().url)
                else Redirect(routes.BusinessActivityTwoController.onPageLoad(NormalMode).url)
            }
          }
        }
      )

    boundResult.recover { case NonFatal(e) =>
      Logger(getClass).error("Error in BusinessActivityCodeTwoController.onSubmit", e)
      BadRequest(view(form.bindFromRequest(), Some(routes.BusinessActivityController.onPageLoad(mode).url), mode))
    }
  }
}
