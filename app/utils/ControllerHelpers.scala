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
import play.api.mvc.{Call, Result}
import pages.QuestionPage
import repositories.SessionRepository
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import play.api.libs.json.Format
import play.api.mvc.Results.*
import models.{CheckMode, Mode, UserAnswers}

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
  def currencyNameAndPrefix(userAnswers: models.UserAnswers, configCurrencyMapping: Map[String, Seq[Currency]])(implicit
    request: DataRequest[?]
  ): (String, String) = CurrencyResolver.currencyNameAndPrefix(userAnswers, configCurrencyMapping)

  // Return a display-friendly currency symbol extracted from session/config.
  // Falls back to the Euro symbol when no symbol can be resolved.
  def currencySymbolFromSession(userAnswers: models.UserAnswers, configCurrencyMapping: Map[String, Seq[Currency]])(implicit
    request: DataRequest[?]
  ): String = {
    // Reuse the name/prefix resolver and pick the symbol portion
    val (_, symbol) = currencyNameAndPrefix(userAnswers, configCurrencyMapping)
    // Fallback to Euro if the resolver returned an empty prefix
    if (symbol.isEmpty) "€" else symbol
  }

  // Generic helper to compare a submitted `value` against a BigDecimal stored
  // on another page in `UserAnswers` using a provided comparator function.
  //
  // Example usage:
  // `compareWithPage(value, TotalPurchaseAmountBeforeVatPage, updated)(_ >= _)`
  def compareWithPage(value: BigDecimal, page: pages.QuestionPage[BigDecimal], updated: models.UserAnswers)(
    cmp: (BigDecimal, BigDecimal) => Boolean
  ): Boolean =
    updated.get(page).exists(stored => cmp(value, stored))

  /** Shared submit helper that centralises the common CheckMode short-circuit pattern used across monetary input controllers.
    *
    * Behaviour:
    *   - If in CheckMode and a `PurchaseTypePage` is present in `userAnswers` the `purchaseCya` call is used as the unchanged-redirect target.
    *   - Otherwise `navigatorNext` is used as the unchanged-redirect target.
    *
    * The `onSaved` continuation is invoked with the updated `UserAnswers` when the value changes (or when not in CheckMode) so callers can decide the
    * appropriate redirect (including any warning-page checks).
    */
  def shortCircuitPersistAndThen[T](
    page: QuestionPage[T],
    newValue: T,
    mode: Mode,
    userAnswers: UserAnswers,
    sessionRepository: SessionRepository,
    navigatorNext: => Call,
    purchaseCya: Call
  )(onSaved: UserAnswers => Future[Result])(implicit fmt: Format[T], ec: ExecutionContext): Future[Result] = {

    // Choose the unchanged-redirect target according to the purchase-journey
    // short-circuit rule that routes CheckMode purchase flows back to the
    // purchase CYA without persisting when the value is unchanged.
    val unchangedRedirect: Call =
      if (mode == models.CheckMode && userAnswers.get(pages.PurchaseTypePage).isDefined) purchaseCya
      else navigatorNext

    CheckModeShortCircuit(page, newValue, mode, userAnswers, sessionRepository, unchangedRedirect, onSaved)
  }

  /** If running in CheckMode and the arrival flag page is not set, set it and persist the updated `UserAnswers`. Otherwise call `render` with the
    * existing `UserAnswers`.
    */
  def markArrivalAndRender(page: QuestionPage[Boolean], mode: Mode, userAnswers: UserAnswers, sessionRepository: SessionRepository)(
    render: UserAnswers => Future[Result]
  )(implicit ec: ExecutionContext, request: DataRequest[?]): Future[Result] = {
    import play.api.mvc.Results.*
    if (mode == CheckMode && !userAnswers.get(page).contains(true)) {
      val markedTry = userAnswers.set(page, true)
      persistAndThen(markedTry, sessionRepository)(render)
    } else render(userAnswers)
  }

}
