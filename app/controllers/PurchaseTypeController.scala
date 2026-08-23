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
import pages.*
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
import utils.{CheckModeShortCircuit, ConfigPurchaseMapping, CountryCode, MountPrefix}
import views.html.PurchaseTypeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** Handles selecting a `PurchaseType` for a purchase flow.
  *
  * Key behaviours documented here so future maintainers can reason quickly:
  *   - When the refunding country was changed we clear the entire purchase chain so downstream pages are recalculated for the new country.
  *   - In `CheckMode` we attempt a short-circuit: if the submitted value is unchanged we redirect to the Purchase CYA without persisting. If the
  *     value changes we compose the necessary `UserAnswers` updates, set a change flag when appropriate, then persist exactly once (single-write
  *     invariant) before performing the CheckMode vs NormalMode redirect.
  *   - When the purchase type changes we clear downstream sub-type/sub-category and description pages so stale data is not kept in session.
  */
class PurchaseTypeController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  config: ConfigPurchaseMapping,
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

  // Compute a back link target for the Purchase Type page. Kept as a
  // tiny helper so the controller action remains concise and the
  // link computation can be mocked/tested separately.
  private def backLink(mode: Mode)(implicit request: DataRequest[?]) =
    // We always return to the 'BeforeYouStartPurchase' page from here
    routes.BeforeYouStartPurchaseController.onPageLoad()
  /*
   * Render the Purchase Type selection page.
   *
   * Behaviour:
   * - If the user changed their refunding country (CountryChangedPage == true)
   *   we clear the entire purchase chain (purchase type, sub-type, labels,
   *   sub-category, describe items) so downstream pages will be recomputed
   *   for the new country. This is done by composing a `Try[UserAnswers]`
   *   (`clearedTry`) which is then persisted and the page re-rendered with
   *   the cleared session state.
   * - Otherwise the stored `PurchaseTypePage` is used to pre-fill the form.
   */
  def onPageLoad(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // If the refunding country was changed we must clear any purchase
    // specific answers so downstream pages will be recalculated for the
    // new country. Compose the clearing as a Try so it can be persisted
    // exactly once below.
    if (request.userAnswers.get(CountryChangedPage).contains(true)) {
      // Compose a `Try` that removes the purchase chain keys in order.
      val clearedTry = for {
        // remove selected purchase type
        afterRemovedPurchaseType <- request.userAnswers.remove(PurchaseTypePage)
        // remove purchase sub-type and its label
        afterRemovedPurchaseSubType    <- afterRemovedPurchaseType.remove(PurchaseSubTypePage)
        afterRemovedPurchaseSubTypeLbl <- afterRemovedPurchaseSubType.remove(PurchaseSubTypeLabelPage)
        // remove purchase sub-category and its label
        afterRemovedPurchaseSubCategory <- afterRemovedPurchaseSubTypeLbl.remove(PurchaseSubCategoryPage)
        afterRemovedPurchaseSubCatLbl   <- afterRemovedPurchaseSubCategory.remove(PurchaseSubCategoryLabelPage)
        // finally remove the CountryChanged flag itself
        afterClearedFlag <- afterRemovedPurchaseSubCatLbl.remove(CountryChangedPage)
      } yield afterClearedFlag

      // Persist the cleared answers once and render the page with the
      // cleared session so the user sees updated (empty) selections.
      Future
        .fromTry(clearedTry)
        .flatMap(updated =>
          sessionRepository
            .set(updated)
            .map(_ => {
              // Prepare the form using the freshly updated session
              val preparedForm = updated.get(PurchaseTypePage).fold(form)(form.fill)
              Ok(view(preparedForm, mode, backLink(mode)))
            })
        )
    } else {
      // Normal rendering: pre-fill the form from session when present
      val preparedForm = request.userAnswers.get(PurchaseTypePage).fold(form)(form.fill)
      Future.successful(Ok(view(preparedForm, mode, backLink(mode))))
    }
  }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        // Validation errors -> render BadRequest with the same back link
        formWithErrors => Future.successful(BadRequest(view(formWithErrors, mode, backLink(mode)))),

        // Successful bind -> process the selected purchase type value
        value => {
          // capture the previous stored purchase type (if any) to decide
          // whether a full downstream clear is necessary
          val previous = request.userAnswers.get(PurchaseTypePage)

          // If the user arrived here from the describe-items change flow
          // we should not short-circuit back to the CYA when the purchase
          // type value is unchanged; instead consume the transient marker
          // and return them to the originating change page so they can
          // continue editing. Arrival flags for sub-type/sub-category are
          // handled after persisting a changed value in
          // `handleCheckModePostPersist` to avoid redirecting on unchanged
          // submissions.
          if (mode == models.CheckMode && previous.contains(value)) {
            // If the user arrived here from editing describe-items we would
            // usually return them to that page. However if they have also
            // travelled via sub-type/sub-category change flows (transient
            // arrival flags present) we should not re-route them back to
            // describe-items on an unchanged submission — instead
            // short-circuit to the CYA. This avoids the loop described in
            // the bug report where users navigate back without making any
            // edits and are sent back to describe-items repeatedly.
            val arrivedFromDescribe = request.userAnswers.get(pages.DescribeItemsArrivedFromCheckYourAnswersPage).contains(true)
            val arrivedFromSubTypeOrCategory = request.userAnswers.get(pages.PurchaseSubTypeArrivedFromCheckYourAnswersPage).contains(true) || request.userAnswers.get(pages.PurchaseSubCategoryArrivedFromCheckYourAnswersPage).contains(true)

            if (arrivedFromDescribe && !arrivedFromSubTypeOrCategory) {
              val removedTry = request.userAnswers.remove(pages.DescribeItemsArrivedFromCheckYourAnswersPage)
              Future.fromTry(removedTry).flatMap { ua =>
                sessionRepository.set(ua).map { _ =>
                  val call = controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(models.CheckMode)
                  val prefix = MountPrefix.get
                  if (prefix.isEmpty || call.url.startsWith(prefix)) Redirect(call)
                  else Redirect(Call(call.method, s"$prefix${call.url}"))
                }
              }
            } else {
              // Default: perform the usual short-circuit to Purchase CYA
              CheckModeShortCircuit.shortCircuitIfUnchanged(
                PurchaseTypePage,
                value,
                mode,
                request.userAnswers,
                controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
              ) match {
                case Some(res) => Future.successful(res)
                case None      =>
                  Future.failed(new IllegalStateException("Expected short-circuit result for unchanged CheckMode submission"))
              }
            }
          }
          else {
            // Submission handling summary:
            // 1. Attempt a CheckMode short-circuit: if the user is in CheckMode
            //    and the submitted value equals the stored value we immediately
            //    redirect to the Purchase CYA without persisting (avoids a
            //    needless write and keeps single-write semantics).
            // 2. If not short-circuited, build a `Try[UserAnswers]` that
            //    represents the new state. If the purchase type changed we
            //    clear downstream data via `buildUpdatedTryForPurchaseTypeChange`.
            // 3. Persist exactly once and perform post-persist routing using
            //    `persistAndHandleSaved` (this may call the external service
            //    to add a purchase, redirect to change-* paths in CheckMode,
            //    or follow the navigator in NormalMode).
            CheckModeShortCircuit.shortCircuitIfUnchanged(
              PurchaseTypePage,
              value,
              mode,
              request.userAnswers,
              controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
            ) match {
              case Some(res) => Future.successful(res)
              case None =>
                val userAnswersTry = request.userAnswers.get(PurchaseTypePage) match {
                  // If the purchase type actually changed, compose a Try that
                  // clears dependent answers (sub-type, sub-category, labels,
                  // and free-text description) before setting the new type.
                  case Some(prev) if prev != value =>
                    // Purchase type changed -> build a Try that clears dependent
                    // sub-type/sub-category and free-text fields before setting
                    // the new type so we persist a consistent session state.
                    buildUpdatedTryForPurchaseTypeChange(value)

                  case _ =>
                    // No change or previously empty -> just set the purchase type
                    request.userAnswers.set(PurchaseTypePage, value)
                }

                // Persist the composed UserAnswers once and handle routing.
                persistAndHandleSaved(userAnswersTry, value, mode)
            }
          }
        }
      )
  }

  // Build a Try[UserAnswers] representing the state when the purchase type
  // changes. This clears downstream pages that are no longer relevant and
  // sets the new `PurchaseTypePage` value. Caller should persist the
  // returned Try once (see `persistAndHandleSaved`).
  private def buildUpdatedTryForPurchaseTypeChange(value: PurchaseType)(implicit request: DataRequest[?]): Try[UserAnswers] =
    /*
     * Compose the update as a `for`-comprehension of `Try` operations so a
     * caller can persist exactly once. Step-by-step intent:
     * - remove PurchaseSubTypePage and its label (these are children of purchase type)
     * - remove PurchaseSubCategoryPage and its label
     * - remove DescribeItemsOnInvoicePage (free text that becomes stale)
     * - set PurchaseTypePage to the new value
     */
    // Compose the sequence of removals and the final set operation. Each
    // step returns a Try[UserAnswers] so the for-comprehension yields a
    // Try representing all changes combined.
    for {
      afterRemovedSubType        <- request.userAnswers.remove(PurchaseSubTypePage)
      afterRemovedSubTypeLabel   <- afterRemovedSubType.remove(PurchaseSubTypeLabelPage)
      afterRemovedSubCategory    <- afterRemovedSubTypeLabel.remove(PurchaseSubCategoryPage)
      afterRemovedSubCategoryLbl <- afterRemovedSubCategory.remove(PurchaseSubCategoryLabelPage)
      afterRemovedDescribe       <- afterRemovedSubCategoryLbl.remove(DescribeItemsOnInvoicePage)
      afterSetPurchaseType       <- afterRemovedDescribe.set(PurchaseTypePage, value)
    } yield afterSetPurchaseType

  // Persist the provided Try[UserAnswers] and then perform post-persist
  // routing. This ensures a single call to `sessionRepository.set` and
  // encapsulates the CheckMode vs NormalMode routing behaviour.
  private def persistAndHandleSaved(userAnswersTry: Try[UserAnswers], value: PurchaseType, mode: Mode)(implicit
    request: DataRequest[?]
  ): Future[Result] =
    // Persist the composed Try once and then dispatch post-persist logic.
    Future.fromTry(userAnswersTry).flatMap { persistedAnswers =>
      /*
       * Persist once and then decide what to do next:
       * - If there is no AddPurchaseResponsePage but we do have a
       *   ClaimApplicationResponsePage, that means we should call the
       *   external `addPurchase` service and persist its response (see
       *   `addPurchaseAndPersist`).
       * - Otherwise, if in CheckMode we may redirect to a change-* URL so
       *   the user can make additional selection for the changed purchase
       *   type (handleCheckModePostPersist).
       * - Otherwise follow the normal navigator (handleNormalModeRedirect).
       */
      // Save the updated answers once
      sessionRepository.set(persistedAnswers).flatMap { _ =>
        // If we need to call the external addPurchase API, do so
        if (persistedAnswers.get(AddPurchaseResponsePage).isEmpty && persistedAnswers.get(queries.ClaimApplicationResponseQuery).isDefined)
          addPurchaseAndPersist(persistedAnswers, value, mode)
        // If running in CheckMode, consider redirecting to change-* or CYA
        else if (mode == models.CheckMode)
          handleCheckModePostPersist(persistedAnswers, value)
        // Otherwise follow normal navigator flow
        else
          handleNormalModeRedirect(persistedAnswers, mode)
      }
    }

  private def handleCheckModePostPersist(updatedAnswers: UserAnswers, value: PurchaseType)(implicit request: DataRequest[?]): Future[Result] = {
    // After persisting in CheckMode, either return to the Purchase CYA or
    // redirect to a change-* path so the user completes any additional
    // required selections for the new purchase type.
    val countryOpt = CountryCode.findCountryCode(updatedAnswers)

    // Determine whether the new purchase type has subcodes configured
    val hasSubcodes = countryOpt
      .flatMap { c =>
        try Some(config.subcodesFor(c, value.toString).nonEmpty)
        catch { case _: Throwable => None }
      }
      .getOrElse(true)

    // If the user reached PurchaseType via the describe-items change flow
    // then consume the transient marker and return them to the describe-items
    // change page so they can edit the free-text description.
    if (updatedAnswers.get(pages.DescribeItemsArrivedFromCheckYourAnswersPage).contains(true)) {
      val removeTry = updatedAnswers.remove(pages.DescribeItemsArrivedFromCheckYourAnswersPage)
      Future.fromTry(removeTry).flatMap { ua =>
        sessionRepository.set(ua).map { _ =>
          val call = controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(models.CheckMode)
          val prefix = MountPrefix.get
          if (prefix.isEmpty || call.url.startsWith(prefix)) Redirect(call)
          else Redirect(Call(call.method, s"$prefix${call.url}"))
        }
      }
    } else if (updatedAnswers.get(pages.PurchaseSubTypeArrivedFromCheckYourAnswersPage).contains(true)) {
      val removeTry = updatedAnswers.remove(pages.PurchaseSubTypeArrivedFromCheckYourAnswersPage)
      Future.fromTry(removeTry).flatMap { ua =>
        sessionRepository.set(ua).map { _ =>
          val call = controllers.purchase.routes.PurchaseSubTypeController.onPageLoad(PurchaseType.slugOf(value), models.CheckMode)
          val prefix = MountPrefix.get
          if (prefix.isEmpty || call.url.startsWith(prefix)) Redirect(call)
          else Redirect(Call(call.method, s"$prefix${call.url}"))
        }
      }
    } else if (updatedAnswers.get(pages.PurchaseSubCategoryArrivedFromCheckYourAnswersPage).contains(true)) {
      val removeTry = updatedAnswers.remove(pages.PurchaseSubCategoryArrivedFromCheckYourAnswersPage)
      Future.fromTry(removeTry).flatMap { ua =>
        sessionRepository.set(ua).map { _ =>
          val call = controllers.purchase.routes.PurchaseSubTypeController.onPageLoad(PurchaseType.slugOf(value), models.CheckMode)
          val prefix = MountPrefix.get
          if (prefix.isEmpty || call.url.startsWith(prefix)) Redirect(call)
          else Redirect(Call(call.method, s"$prefix${call.url}"))
        }
      }
    } else if (!hasSubcodes) Future.successful(Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()))
    else {
      // Build a change-<slug> path and redirect there so users complete
      // any newly-required pages for the changed purchase type.
      val slug = PurchaseType.slugOf(value)
      val prefix = MountPrefix.get
      val changePath = s"${if (prefix.isEmpty) "" else prefix}/change-$slug"
      Future.successful(Redirect(Call("GET", changePath)))
    }
  }

  private def handleNormalModeRedirect(updatedAnswers: UserAnswers, mode: Mode)(implicit request: DataRequest[?]): Future[Result] = {
    // NormalMode redirect: compute the navigator target and apply the
    // mount prefix if the application is hosted under a non-root path.
    val call = navigator.nextPage(PurchaseTypePage, mode, updatedAnswers)
    val prefix = MountPrefix.get
    if (prefix.isEmpty || call.url.startsWith(prefix)) Future.successful(Redirect(call))
    else Future.successful(Redirect(Call(call.method, s"$prefix${call.url}")))
  }

  private def addPurchaseAndPersist(
    answers: UserAnswers,
    purchaseType: PurchaseType,
    mode: Mode
  )(implicit request: DataRequest[?]): Future[Result] = {

    // Build an implicit HeaderCarrier from the request/session for the
    // downstream HTTP client used by `euVatRefundsService`.
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    // If the ClaimApplicationResponse is missing we cannot call the API.
    // Log and recover by redirecting to journey recovery.
    answers
      .get(ClaimApplicationResponseQuery)
      .fold {
        logger.warn("Missing applicationId for addPurchase")
        Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
      } { claimResponse =>

        // Build a minimal AddPurchaseRequest using the available information
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

        // Call the external service, persist its response and redirect
        // according to the navigator. Failures are logged and redirect to
        // journey recovery to avoid leaving the user in an inconsistent state.
        euVatRefundsService
          .addPurchase(purchaseRequest)
          .flatMap { response =>
            for {
              // persist the API response in session
              updatedAnswers <- Future.fromTry(
                                  answers.set(AddPurchaseResponsePage, response)
                                )
              _ <- sessionRepository.set(updatedAnswers)
            } yield {
              // Navigate to the next page, applying mount prefix when needed
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
            // Log unexpected errors and redirect to recovery
            logger.error("Error while adding the purchase", ex)
            Redirect(routes.JourneyRecoveryController.onPageLoad())
          }
      }
  }
}
