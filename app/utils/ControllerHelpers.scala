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

import models.requests.DataRequest
import play.api.data.Form
import play.api.mvc.Result
import repositories.SessionRepository
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import play.api.mvc.Results.*

object ControllerHelpers {

  // Prepare a form by reading a value out of UserAnswers (if present) and
  // returning either the empty form or the form pre-filled with the stored value.
  //
  // Parameters:
  // - getFromAnswers: a function extracting an Option[T] from UserAnswers. We
  //   avoid passing a generic QuestionPage[T] here to sidestep JSON Reads
  //   resolution requirements in this helper and make the call site explicit.
  // - form: the Play form instance to fill or return unchanged.
  // - implicit request: DataRequest[_] so we can access `request.userAnswers`.
  def preparedFormFromAnswers[T](getFromAnswers: models.UserAnswers => Option[T], form: Form[T])(implicit
    request: DataRequest[?]
  ): Form[T] = {
    // Try to extract the stored value using the provided getter
    getFromAnswers(request.userAnswers) match {
      // No stored value: return the untouched form (empty input)
      case None => form
      // Stored value exists: return a form filled with that value
      case Some(value) => form.fill(value)
    }
  }

  // Persist a Try[UserAnswers] exactly once (sessionRepository.set) and then
  // invoke the continuation `f` with the persisted answers. This enforces the
  // single-write-per-user-action invariant used across controllers.
  //
  // Parameters:
  // - userAnswersTry: a Try-wrapped UserAnswers built by composing page updates
  // - sessionRepository: repository used to persist the built UserAnswers
  // - f: continuation to run after persistence; receives the persisted UserAnswers
  // - implicit ec/request: ExecutionContext for futures and DataRequest for access
  //   to the current request and its userAnswers when needed inside `f`.
  def persistAndThen(userAnswersTry: Try[models.UserAnswers], sessionRepository: SessionRepository)(
    f: models.UserAnswers => Future[Result]
  )(implicit ec: ExecutionContext, request: DataRequest[?]): Future[Result] = {
    // Convert the Try to a Future and sequence the set + continuation
    for {
      // Build the UserAnswers or short-circuit with a failed Future
      built <- Future.fromTry(userAnswersTry)
      // Persist the built answers exactly once
      _ <- sessionRepository.set(built)
      // Run the continuation and return its result
      res <- f(built)
    } yield res
  }

  // Resolve the human-friendly currency name and prefix for views using the
  // central CurrencyResolver. This helper simply delegates and provides a
  // consistent signature for controllers to call.
  def currencyNameAndPrefix(userAnswers: models.UserAnswers, configCurrencyMapping: ConfigCurrencyMapping)(implicit
    request: DataRequest[?]
  ): (String, String) = CurrencyResolver.currencyNameAndPrefix(userAnswers, configCurrencyMapping)

  // Return a display-friendly currency symbol extracted from session/config.
  // Falls back to the Euro symbol when no symbol can be resolved.
  def currencySymbolFromSession(userAnswers: models.UserAnswers, configCurrencyMapping: ConfigCurrencyMapping)(implicit
    request: DataRequest[?]
  ): String = {
    // Reuse the name/prefix resolver and pick the symbol portion
    val (_, symbol) = currencyNameAndPrefix(userAnswers, configCurrencyMapping)
    // Fallback to Euro if the resolver returned an empty prefix
    if (symbol.isEmpty) "€" else symbol
  }

}
