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

package utils

import models.UserAnswers
import repositories.SessionRepository
import play.api.mvc.{Call, Result}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import play.api.mvc.Results.InternalServerError

object SaveAndRedirect {

  /** Persist a Try[UserAnswers] and redirect to the provided call.
    *
    * This helper centralises the common pattern used across controllers where several transformations are composed into a `Try[UserAnswers]` and the
    * caller wants to persist the successful result and redirect to a known `Call` (page). It returns an `InternalServerError` when either the `Try`
    * is a Failure or the repository persist fails.
    *
    * @param userAnswersTry
    *   a Try-wrapped UserAnswers built by the caller
    * @param sessionRepository
    *   repository used to persist the UserAnswers
    * @param nextPage
    *   the Call to redirect to after successful persist
    */
  def saveTryAndRedirect(userAnswersTry: Try[UserAnswers], sessionRepository: SessionRepository, nextPage: Call)(implicit
    ec: ExecutionContext
  ): Future[Result] =
    userAnswersTry match {
      // Successfully built UserAnswers: persist to session repository
      case scala.util.Success(userAnswers) =>
        sessionRepository
          .set(userAnswers)
          .map(_ => play.api.mvc.Results.Redirect(nextPage))
          .recover { case _ => InternalServerError("Failed to persist session") }

      // Could not build UserAnswers (validation / composition failure)
      case scala.util.Failure(_) =>
        Future.successful(InternalServerError("Failed to build UserAnswers"))
    }
}
