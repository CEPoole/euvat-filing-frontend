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
import forms.PurchaseSubTypeFormProvider
import navigation.Navigator
import pages.{PurchaseSubTypePage, PurchaseTypePage, PurchaseSubCategoryPage, PurchaseSubCategoryLabelPage, PurchaseSubTypeLabelPage, CountryChangedPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import models.PurchaseType
import models.PurchaseSubCategoryType
import models.{Mode, UserAnswers}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.ConfigPurchaseMapping
import views.html.PurchaseSubTypeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class PurchaseSubCategoryController @Inject() (
  override val messagesApi: MessagesApi,
  sessionRepository: SessionRepository,
  navigator: Navigator,
  identify: IdentifierAction,
  getData: DataRetrievalAction,
  requireData: DataRequiredAction,
  formProvider: PurchaseSubTypeFormProvider,
  config: ConfigPurchaseMapping,
  val controllerComponents: MessagesControllerComponents,
  view: PurchaseSubTypeView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with play.api.Logging {

  val form = formProvider()

  /**
   * Remove an internal numeric segment from a message key.
   *
   * Some label keys include an extra numeric segment for ordering (eg
   * "purchase.sub.1.some.code.title"). This helper returns a key with that
   * numeric segment removed when it matches the expected prefix pattern.
   */
  private def stripLeadingNumeric(key: String): String = {
    val parts = key.split("\\.")
    if (parts.length >= 5 && parts.head == "purchase" && parts(1) == "sub") (parts.take(3) ++ parts.drop(4)).mkString(".")
    else key
  }

  /**
   * Resolve a human-readable title for a label key.
   *
   * Tries the original message key then a stripped variant (without numeric
   * ordering segments). Returns `None` when no message is defined for either.
   */
  private def titleForLabelKey(labelKey: String, msgs: play.api.i18n.Messages): Option[String] = {
    val original = s"${labelKey}.title"
    val stripped = s"${stripLeadingNumeric(labelKey)}.title"
    Seq(original, stripped).collectFirst { case k if msgs.isDefinedAt(k) => msgs(k) }
  }

  /**
   * Attempt to derive a parent heading from message keys based on the
   * resolved parent code. Tries several variants to be tolerant of different
   * key shapes (full dotted code, code without leading segment, last segment).
   */
  private def parentDerivedTitle(parentKey: String, resolvedParentCode: String, msgs: play.api.i18n.Messages): Option[String] = {
    val asIs = s"purchase.sub.${parentKey}.${resolvedParentCode}.title"
    val dropLeading = {
      val parts = resolvedParentCode.split("\\.")
      if (parts.length > 1) s"purchase.sub.${parentKey}.${parts.drop(1).mkString(".")}.title" else asIs
    }
    val lastSeg = resolvedParentCode.split("\\.").lastOption.map(s => s"purchase.sub.${parentKey}.${s}.title").getOrElse(asIs)
    Seq(asIs, dropLeading, lastSeg).collectFirst { case k if msgs.isDefinedAt(k) => msgs(k) }
  }

  /**
   * Try to construct a mounted `Call` for a candidate child route.
   *
   * When routers are mounted the usual reverse routing may not be available
   * in all contexts (eg tests). This helper attempts to compute the friendly
   * slug and build a `Call` using the current request path as a prefix.
   * It returns `None` if slug construction fails for any reason.
   */
  private def tryReverseParent(parentKey: String, candidate: String)(implicit request: play.api.mvc.RequestHeader): Option[play.api.mvc.Call] = {
    try {
      val slug = PurchaseSubCategoryType.pathFor(parentKey, candidate)
      val prefix = request.path.lastIndexOf('/') match {
        case i if i > 0 => request.path.substring(0, i)
        case _           => ""
      }
      Some(play.api.mvc.Call("POST", s"$prefix/$slug"))
    } catch { case _: Throwable => None }
  }

  /**
   * Compute the `Call` that should back the form's POST action.
   *
   * Attempts to reverse any candidate mounted child routes and falls back to
   * posting to the friendly `purchaseTypeSlug` path when none can be
   * resolved.
   */
  private def computeFormAction(purchaseTypeSlug: String, candidates: Seq[String])(implicit request: play.api.mvc.RequestHeader): play.api.mvc.Call =
    candidates.iterator.flatMap(c => tryReverseParent(purchaseTypeSlug, c)).find(_ => true).getOrElse(play.api.mvc.Call("POST", s"/${purchaseTypeSlug}"))

  /**
   * Back-link URL for the given purchase-type slug. Uses a simple GET `Call`
   * to avoid depending on reverse routes being present when routers are
   * mounted in a parent router.
   */
  private def backUrlFor(purchaseTypeSlug: String): String = play.api.mvc.Call("GET", s"/${purchaseTypeSlug}").url

  /**
   * Search for a subcode whose last dotted segment matches `seg`.
   *
   * Used as a fallback when a full dotted code isn't available but the last
   * segment of a stored code matches the requested identifier.
   */
  private def findByLastSegment(parentKey: String, seg: String, country: String): Option[String] =
    config.subcodesFor(country, parentKey).map(_._1).find(code => code.split("\\.").lastOption.contains(seg))

  /**
   * Determine which parent code actually yields subcategory options and
   * return the resolved parent plus the option list.
   *
   * Strategy:
   * 1. Try `effectiveParentCode` (session-preferred or URL-provided).
   * 2. If empty, try the `effectiveParentCode` without its leading segment.
   * 3. If still empty, try to locate a code by matching the last segment.
   * 4. Otherwise return the original parentCode with whatever options were
   *    found (possibly empty).
   */
  private def computeResolvedParentAndOptions(parentKey: String, effectiveParentCode: String, parentCode: String, country: String): (String, Seq[(String, String)]) = {
    val initialOptions = config.subcategoriesFor(country, parentKey, effectiveParentCode)
    if (initialOptions.nonEmpty) (effectiveParentCode, initialOptions)
    else {
      val alt = effectiveParentCode.split("\\.").drop(1).mkString(".")
      val altOptions = if (alt.nonEmpty) config.subcategoriesFor(country, parentKey, alt) else Seq.empty
      if (altOptions.nonEmpty) (alt, altOptions)
      else findByLastSegment(parentKey, parentCode, country).map(found => (found, config.subcategoriesFor(country, parentKey, found))).getOrElse((parentCode, initialOptions))
    }
  }

  // NEW: Consolidated helper that prepares all view-state for both
  // `onPageLoad` and `onSubmit` to keep rendering consistent and make each
  // controller action easier to test in isolation.
  /**
   * Prepare all data required to render the Purchase SubCategory page.
   *
   * Inputs:
   * - `purchaseTypeSlug`: the friendly slug used in routes (e.g. `/fuel-type`).
   * - `parentKey`: canonical parent key (e.g. `fuel`).
   * - `parentCode`: dotted parent-code passed in the URL as fallback.
   * - `effectiveParentCode`: parent code preferred from session when present.
   * - `country`: resolved ISO country code used to load country-specific options.
   * - `userAnswers`: the session-backed `UserAnswers` used to populate form state.
   *
   * Returns a 11-tuple:
   * (resolvedParentCode, options, radioItems, pageTitle, heading, preparedForm,
   *  formAction, backUrl, parentBase, childToPersist, parentLabelKeyOpt)
   */
  private def prepareSubCategoryViewData(purchaseTypeSlug: String, parentKey: String, parentCode: String, effectiveParentCode: String, country: String, userAnswers: UserAnswers)(implicit request: play.api.mvc.RequestHeader) = {
    val msgs = messagesApi.preferred(request)

    // Resolve which parent code actually yields options, with fallbacks.
    val (resolvedParentCode, options) = computeResolvedParentAndOptions(parentKey, effectiveParentCode, parentCode, country)

    // Build radio items for the view
    val items = config.buildRadioItems(options, msgs)

    // Resolve heading/title candidates
    val childTitleOpt = options.to(LazyList).flatMap { case (_, labelKey) => titleForLabelKey(labelKey, msgs) }.headOption
    val parentLabelKeyOpt = config.subcodesFor(country, parentKey).find(_._1 == resolvedParentCode).map(_._2)
    val parentHeading = msgs(s"purchase.sub.${parentKey}.heading")
    val heading = childTitleOpt.orElse(parentDerivedTitle(parentKey, resolvedParentCode, msgs)).getOrElse(parentHeading)
    val pageTitle = heading

    // Form state
    val preparedForm = userAnswers.get(PurchaseSubCategoryPage).fold(form)(form.fill)

    // Candidate route slugs to attempt reversing to a mounted purchase.routes
    val head = resolvedParentCode.split("\\.").headOption.getOrElse(resolvedParentCode)
    val last = resolvedParentCode.split("\\.").lastOption.getOrElse(resolvedParentCode)
    val candidates = Seq(resolvedParentCode, last, head).distinct

    val formAction = computeFormAction(purchaseTypeSlug, candidates)(request)
    val backUrl = backUrlFor(purchaseTypeSlug)

    val parentBase = resolvedParentCode.split("\\.").headOption.getOrElse(resolvedParentCode)

    val childToPersist = if (resolvedParentCode.contains(".")) resolvedParentCode else options.headOption.map(_._1).getOrElse(resolvedParentCode)

    (resolvedParentCode, options, items, pageTitle, heading, preparedForm, formAction, backUrl, parentBase, childToPersist, parentLabelKeyOpt)
  }

  /**
   * Resolve the ISO country code from session state.
   *
   * Preference order:
   * 1. `RefundingCountryPage` (expected to hold the ISO code).
   * 2. `RefundingCountryNamePage` which may contain either "CODE,Name" or
   *    "Name,CODE" — in that case prefer the last token as the ISO code.
   */
  private def resolveCountryCode(userAnswers: UserAnswers): Option[String] =
    userAnswers.get(pages.RefundingCountryPage).orElse {
      userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
        // `RefundingCountryNamePage` may contain "CODE,Name" or "Name,CODE".
        // Use the last token after a comma when present to prefer the ISO code.
        val parts = stored.split(",", 2).map(_.trim)
        if (parts.length > 1) parts.last else stored
      }
    }

  def onPageLoad(purchaseTypeSlug: String, parentCode: String, mode: Mode): Action[AnyContent] =
    (identify andThen getData andThen requireData).async { implicit request =>

      // If the country was changed, clear dependent child selection and reload
      if (request.userAnswers.get(pages.CountryChangedPage).contains(true)) {
        // Clear child selection and the country-changed flag when the user has
        // changed the country so that subsequent pages render the correct
        // country-specific options.
        val clearedAnswers = for {
          afterRemovedSubCategory      <- request.userAnswers.remove(PurchaseSubCategoryPage)
          afterRemovedSubCategoryLabel <- afterRemovedSubCategory.remove(PurchaseSubCategoryLabelPage)
          afterClearedFlag             <- afterRemovedSubCategoryLabel.remove(pages.CountryChangedPage)
        } yield afterClearedFlag

        // Redirect back to the current friendly slug path so the view reloads
        // using the new country context.
        Future.fromTry(clearedAnswers).flatMap(updated => sessionRepository.set(updated).map(_ => Redirect(play.api.mvc.Call("GET", request.path))))
      } else {
        val maybeParent = PurchaseType.fromSlug(purchaseTypeSlug).map(_.toString)
        val maybeCountry = resolveCountryCode(request.userAnswers)

        (maybeParent, maybeCountry) match {
          case (Some(parentKey), Some(country)) =>
            // Prefer the parent code stored in session (PurchaseSubTypePage) when
            // present. This lets us route to slug-only paths (e.g. /fuel-type)
            // without needing to pass the dotted parent code as a query param.
            val parentFromSessionOpt = request.userAnswers.get(pages.PurchaseSubTypePage)
            val effectiveParentCode = parentFromSessionOpt.getOrElse(parentCode)

            // determine candidate options for the given parent code
            val (resolvedParentCode, options) = computeResolvedParentAndOptions(parentKey, effectiveParentCode, parentCode, country)

            if (options.isEmpty) Future.successful(Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode)))
            else {
              val msgs = messagesApi.preferred(request)
              val items = config.buildRadioItems(options, msgs)

              val childTitleOpt = options.to(LazyList).flatMap { case (_, labelKey) => titleForLabelKey(labelKey, msgs) }.headOption
              val parentLabelKeyOpt = config.subcodesFor(country, parentKey).find(_._1 == resolvedParentCode).map(_._2)
              val parentHeading = msgs(s"purchase.sub.${parentKey}.heading")
              val heading = childTitleOpt.orElse(parentDerivedTitle(parentKey, resolvedParentCode, msgs)).getOrElse(parentHeading)
              val pageTitle = heading

              val (resolvedParentCode2, options2, items2, pageTitle2, heading2, preparedForm2, formAction2, backUrl2, parentBase2, childToPersist2, parentLabelKeyOpt2) =
                prepareSubCategoryViewData(purchaseTypeSlug, parentKey, parentCode, effectiveParentCode, country, request.userAnswers)(request)

              // If the session already contains a matching parent base we simply
              // render the page. Otherwise, persist a default parent selection so
              // subsequent steps have a canonical parent value to reference.
              request.userAnswers.get(pages.PurchaseSubTypePage) match {
                case Some(existing) if existing.split("\\.").headOption.contains(parentBase2) =>
                  Future.successful(Ok(view(preparedForm2, items2, pageTitle2, heading2, formAction2, backUrl2)))
                case _ =>
                  val labelForParent = parentLabelKeyOpt2.flatMap(k => Some(messagesApi.preferred(request)(k))).getOrElse(childToPersist2)
                  val saved = for {
                    afterSetParent       <- request.userAnswers.set(pages.PurchaseSubTypePage, childToPersist2)
                    afterSetParentLabel  <- afterSetParent.set(pages.PurchaseSubTypeLabelPage, labelForParent)
                  } yield afterSetParentLabel

                  Future.fromTry(saved).flatMap(finalAnswers => sessionRepository.set(finalAnswers).map(_ => Ok(view(preparedForm2, items2, pageTitle2, heading2, formAction2, backUrl2))))
              }
            }

          case _ => Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
        }
      }
  }

  def onSubmit(purchaseTypeSlug: String, parentCode: String, mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val maybeParent = PurchaseType.fromSlug(purchaseTypeSlug).map(_.toString)
    val maybeCountry = resolveCountryCode(request.userAnswers)

    (maybeParent, maybeCountry) match {
      case (Some(parentKey), Some(country)) =>
        val parentFromSessionOpt = request.userAnswers.get(pages.PurchaseSubTypePage)
        val effectiveParentCode = parentFromSessionOpt.getOrElse(parentCode)

        val (resolvedParentCode, options) = computeResolvedParentAndOptions(parentKey, effectiveParentCode, parentCode, country)

        if (options.isEmpty) Future.successful(Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode)))
        else {
          val (resolvedParentCode2, options2, items2, pageTitle2, heading2, preparedForm2, formAction2, backUrl2, parentBase2, childToPersist2, parentLabelKeyOpt2) =
            prepareSubCategoryViewData(purchaseTypeSlug, parentKey, parentCode, effectiveParentCode, country, request.userAnswers)(request)

          val msgs = messagesApi.preferred(request)

          form
            .bindFromRequest()
            .fold(
              formWithErrors => Future.successful(BadRequest(view(formWithErrors, items2, pageTitle2, heading2, formAction2, backUrl2))),
              value => {
                val labelKeyOpt = config.subcategoriesFor(country, parentKey, resolvedParentCode2).find(_._1 == value).map(_._2)
                val label = labelKeyOpt.map(k => msgs(k)).getOrElse(value)

                val saved = for {
                  afterSetChild      <- request.userAnswers.set(PurchaseSubCategoryPage, value)
                  afterSetChildLabel <- afterSetChild.set(PurchaseSubCategoryLabelPage, label)
                } yield afterSetChildLabel

                for {
                  updatedAnswers <- Future.fromTry(saved)
                  _              <- sessionRepository.set(updatedAnswers)
                } yield {
                  // Always continue to Invoice Type from PurchaseSubCategory. The
                  // behaviour that routes to Describe Items for `Other`+`99` is
                  // handled from the PurchaseSubType flow when no subcategories
                  // exist, so this page should consistently go to InvoiceType.
                  Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode))
                }
              }
            )
        }

      case _ => Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
    }
  }
}
