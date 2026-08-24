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

package controllers.purchase

import controllers.actions.*
import controllers.routes
import forms.PurchaseSubTypeFormProvider
import models.{Mode, PurchaseSubCategoryType, PurchaseType, UserAnswers}
import navigation.Navigator
import pages.{PurchaseSubCategoryArrivedFromCheckYourAnswersPage, PurchaseSubCategoryLabelPage, PurchaseSubCategoryPage, PurchaseTypePage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.ConfigPurchaseMapping
import utils.ControllerHelpers
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

  // instantiate the form using the injected provider
  val form = formProvider()

  // stripLeadingNumeric: helper to normalise label keys that include
  // a leading numeric segment used in some config keys. This keeps
  // message lookup consistent when keys are generated with an extra
  // numeric prefix.
  private def stripLeadingNumeric(key: String): String = {
    // split the dotted key into parts
    val parts = key.split("\\.")
    // if the key looks like purchase.sub.X.Y.Z where an extra numeric
    // prefix was inserted, drop that segment for lookup
    if (parts.length >= 5 && parts.head == "purchase" && parts(1) == "sub") (parts.take(3) ++ parts.drop(4)).mkString(".")
    else key
  }

  /** Purchase sub-category selection controller.
    *
    * Key responsibilities:
    *   - Resolve parent purchase type and country from session or request and build the available options from `ConfigPurchaseMapping`.
    *   - Render an appropriately labelled radio list with context-aware titles and error keys (helpers such as `titleForLabelKey` and
    *     `parentDerivedTitle` encapsulate that logic).
    *   - Persist a single composed `Try[UserAnswers]` when the user selects an option and then redirect according to `mode` (CheckMode vs
    *     NormalMode). The `persistAndThen` helper centralises the single write pattern (compose -> persist -> continue).
    */

  private def titleForLabelKey(labelKey: String, msgs: play.api.i18n.Messages): Option[String] = {
    // build candidate message keys: the raw label key and a stripped variant
    val original = s"$labelKey.title"
    val stripped = s"${stripLeadingNumeric(labelKey)}.title"
    // return the first defined message for those candidate keys
    Seq(original, stripped).collectFirst { case k if msgs.isDefinedAt(k) => msgs(k) }
  }

  private def parentDerivedTitle(parentKey: String, resolvedParentCode: String, msgs: play.api.i18n.Messages): Option[String] = {
    // attempt several keys to derive a parent title based on different
    // granularities of the resolved code (full, drop leading segment, last segment)
    val asIs = s"purchase.sub.$parentKey.$resolvedParentCode.title"
    val dropLeading = {
      // if resolved code contains multiple segments, drop the first and try
      val parts = resolvedParentCode.split("\\.")
      if (parts.length > 1) s"purchase.sub.$parentKey.${parts.drop(1).mkString(".")}.title" else asIs
    }
    // lastSeg is just the final segment, used by some localized keys
    val lastSeg = resolvedParentCode.split("\\.").lastOption.map(s => s"purchase.sub.$parentKey.$s.title").getOrElse(asIs)
    // return the first key that exists in messages
    Seq(asIs, dropLeading, lastSeg).collectFirst { case k if msgs.isDefinedAt(k) => msgs(k) }
  }

  private def tryReverseParent(parentKey: String, candidate: String, mode: Mode)(implicit request: play.api.mvc.RequestHeader): Option[Call] = {
    try {
      // attempt to compute the route slug for this parent/candidate pair
      val slug = PurchaseSubCategoryType.pathFor(parentKey, candidate)
      // mount prefix may be set for this application
      val prefix = utils.MountPrefix.get
      // construct a change- prefixed path if running in CheckMode
      val url =
        if (mode == models.CheckMode) if (prefix.isEmpty) s"/change-$slug" else s"$prefix/change-$slug"
        else if (prefix.isEmpty) s"/$slug"
        else s"$prefix/$slug"
      // return a POST call with the computed URL
      Some(Call("POST", url))
    } catch { case _: Throwable => None /* return None when slug computation fails */ }
  }

  private def computeFormAction(parentKey: String, candidates: Seq[String], userAnswers: UserAnswers, mode: Mode)(implicit
    request: play.api.mvc.RequestHeader
  ): Call = {
    // compute mount prefix and session slug candidate
    val prefix = utils.MountPrefix.get
    val maybeSessionSlug = userAnswers.get(PurchaseTypePage).map(models.PurchaseType.slugOf)
    // try reversing using candidate codes first; if none succeed fall back
    // to a slug derived from the session PurchaseType or to root
    candidates.iterator
      .flatMap(c => tryReverseParent(parentKey, c, mode))
      .find(_ => true)
      .getOrElse(
        maybeSessionSlug
          .map { slug =>
            val url =
              if (mode == models.CheckMode) if (prefix.isEmpty) s"/change-$slug" else s"$prefix/change-$slug"
              else if (prefix.isEmpty) s"/$slug"
              else s"$prefix/$slug"
            Call("POST", url)
          }
          .getOrElse(Call("POST", if (prefix.isEmpty) s"/" else s"$prefix/"))
      )
  }

  private def backUrlFor(userAnswers: UserAnswers, mode: Mode)(implicit request: play.api.mvc.RequestHeader): String = {
    // compute the back URL that returns to the parent purchase type
    // when in CheckMode the back target should include the change- prefix
    val prefix = utils.MountPrefix.get
    userAnswers.get(PurchaseTypePage).map(models.PurchaseType.slugOf) match {
      case Some(slug) =>
        val url = if (mode == models.CheckMode) {
          if (prefix.isEmpty) s"/change-$slug" else s"$prefix/change-$slug"
        } else {
          if (prefix.isEmpty) s"/$slug" else s"$prefix/$slug"
        }
        Call("GET", url).url
      case None =>
        // fallback to the top-level PurchaseType page in NormalMode
        controllers.routes.PurchaseTypeController.onPageLoad(models.NormalMode).url
    }
  }

  private def findByLastSegment(parentKey: String, seg: String, country: String): Option[String] =
    // search the configured subcodes for one whose final segment matches `seg`
    config.subcodesFor(country, parentKey).map(_._1).find(code => code.split("\\.").lastOption.contains(seg))

  private def computeResolvedParentAndOptions(parentKey: String,
                                              effectiveParentCode: String,
                                              parentCode: String,
                                              country: String
                                             ): (String, Seq[(String, String)]) = {
    // attempt to get options for the provided effective parent code
    val initialOptions = config.subcategoriesFor(country, parentKey, effectiveParentCode)
    if (initialOptions.nonEmpty) (effectiveParentCode, initialOptions)
    else {
      // if no options, try dropping the first segment of the effective code
      val alt = effectiveParentCode.split("\\.").drop(1).mkString(".")
      val altOptions = if (alt.nonEmpty) config.subcategoriesFor(country, parentKey, alt) else Seq.empty
      if (altOptions.nonEmpty) (alt, altOptions)
      else
        // as a final fallback try to locate an option by matching the last segment
        findByLastSegment(parentKey, parentCode, country)
          .map(found => (found, config.subcategoriesFor(country, parentKey, found)))
          .getOrElse((parentCode, initialOptions))
    }
  }

  private def prepareSubCategoryViewData(parentKey: String,
                                         parentCode: String,
                                         effectiveParentCode: String,
                                         country: String,
                                         userAnswers: UserAnswers,
                                         mode: Mode
                                        )(implicit request: play.api.mvc.RequestHeader) = {
    // obtain the preferred messages for the current request
    val msgs = messagesApi.preferred(request)

    // resolve a parent code and its available options
    val (resolvedParentCode, options) = computeResolvedParentAndOptions(parentKey, effectiveParentCode, parentCode, country)

    // build radio items from config options for rendering
    val items = config.buildRadioItems(options, msgs)

    // compute last and head segments for title/error resolution
    val lastSeg = resolvedParentCode.split("\\.").lastOption.getOrElse(resolvedParentCode)
    val headSeg = resolvedParentCode.split("\\.").headOption.getOrElse(resolvedParentCode)

    // Prefer a child-specific title, then a fully-qualified title, then head segment
    val specificTitleKeys = Seq(
      s"purchase.sub.$parentKey.$lastSeg.title",
      s"purchase.sub.$parentKey.$resolvedParentCode.title",
      s"purchase.sub.$parentKey.$headSeg.title"
    )

    // try the specific title keys, otherwise derive from option label keys
    val childTitleOpt = specificTitleKeys
      .collectFirst { case k if msgs.isDefinedAt(k) => msgs(k) }
      .orElse(options.to(LazyList).flatMap { case (_, labelKey) => titleForLabelKey(labelKey, msgs) }.headOption)

    // parent label key (if present) and heading fallback
    val parentLabelKeyOpt = config.subcodesFor(country, parentKey).find(_._1 == resolvedParentCode).map(_._2)
    val parentHeading = msgs(s"purchase.sub.$parentKey.heading")
    val heading = childTitleOpt.orElse(parentDerivedTitle(parentKey, resolvedParentCode, msgs)).getOrElse(parentHeading)
    val pageTitle = heading

    // determine an appropriate error message key for required validation
    val candidateKeys = Seq(
      s"purchase.sub.$parentKey.$lastSeg.error.required",
      s"purchase.sub.$parentKey.error.required"
    )
    val requiredKey = candidateKeys.find(k => msgs.isDefinedAt(k)).getOrElse("error.required")
    // prepare the form, filling with any existing answer
    val preparedForm = userAnswers.get(PurchaseSubCategoryPage).fold(formProvider(requiredKey))(formProvider(requiredKey).fill)

    // candidates used to compute the form action route
    val head = resolvedParentCode.split("\\.").headOption.getOrElse(resolvedParentCode)
    val last = resolvedParentCode.split("\\.").lastOption.getOrElse(resolvedParentCode)
    val candidates = Seq(resolvedParentCode, last, head).distinct

    // compute the POST action target for the radio form
    val formAction = computeFormAction(parentKey, candidates, userAnswers, mode)(request)
    // compute a back URL used by the template (mode-aware)
    val backUrl = backUrlFor(userAnswers, mode)

    // parent base is the top-level segment of the resolved code
    val parentBase = resolvedParentCode.split("\\.").headOption.getOrElse(resolvedParentCode)

    // decide which child code to persist when auto-setting parent
    val childToPersist = if (resolvedParentCode.contains(".")) resolvedParentCode else options.headOption.map(_._1).getOrElse(resolvedParentCode)

    // return a tuple of all derived view data
    (resolvedParentCode, options, items, pageTitle, heading, preparedForm, formAction, backUrl, parentBase, childToPersist, parentLabelKeyOpt)
  }

  // Persist the provided `Try[UserAnswers]` once, then invoke `f` with the
  // persisted `UserAnswers`. Delegates to the central `ControllerHelpers.persistAndThen` to avoid duplication.

  // Helper: compute effectiveParentCode for the current request/session
  private def effectiveParentCodeFor(country: String, parentKey: String, userAnswers: UserAnswers): String =
    userAnswers.get(pages.PurchaseSubTypePage).getOrElse {
      // fallback to the first configured subcode when session missing
      config.subcodesFor(country, parentKey).headOption.map(_._1).getOrElse("")
    }

  def onPageLoad(mode: Mode): Action[AnyContent] =
    (identify andThen getData andThen requireData).async { implicit request =>
      // If the country has changed, clear dependent subcategory values
      if (request.userAnswers.get(pages.CountryChangedPage).contains(true)) {
        val clearedAnswers = for {
          afterRemovedSubCategory      <- request.userAnswers.remove(PurchaseSubCategoryPage)
          afterRemovedSubCategoryLabel <- afterRemovedSubCategory.remove(PurchaseSubCategoryLabelPage)
          afterClearedFlag             <- afterRemovedSubCategoryLabel.remove(pages.CountryChangedPage)
        } yield afterClearedFlag

        // persist the cleared answers and redirect back to the same path
        Future.fromTry(clearedAnswers).flatMap { updated =>
          sessionRepository.set(updated).map(_ => Redirect(Call("GET", request.path)))
        }

      } else {
        // otherwise, proceed to compute the parent and country from session
        val maybeParent = request.userAnswers.get(pages.PurchaseTypePage).map(_.toString)
        val maybeCountry = utils.CountryCode.findCountryCode(request.userAnswers)

        (maybeParent, maybeCountry) match {
          case (Some(parentKey), Some(country)) =>
            // compute effective parent code (either from session or config)
            val effectiveParentCode = effectiveParentCodeFor(country, parentKey, request.userAnswers)

            // if there are no options for this parent, redirect to invoice type
            val (resolvedParentCode, options) = computeResolvedParentAndOptions(parentKey, effectiveParentCode, effectiveParentCode, country)

            if (options.isEmpty)
              Future.successful(
                if (mode == models.CheckMode) Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
                else Redirect(routes.InvoiceTypeController.onPageLoad(mode))
              )
            else {
              // prepare view data used when rendering the radio list
              val (resolvedParentCode2,
                   options2,
                   items2,
                   pageTitle2,
                   heading2,
                   preparedForm2,
                   formAction2,
                   backUrl2,
                   parentBase2,
                   childToPersist2,
                   parentLabelKeyOpt2
                  ) =
                prepareSubCategoryViewData(parentKey, effectiveParentCode, effectiveParentCode, country, request.userAnswers, mode)(request)

              // if session already contains a compatible parent, just render
              request.userAnswers.get(pages.PurchaseSubTypePage) match {
                case Some(existing) if existing.split("\\.").headOption.contains(parentBase2) =>
                  // Use helper to mark arrival flag when entering in CheckMode
                  ControllerHelpers.markArrivalAndRender(
                    PurchaseSubCategoryArrivedFromCheckYourAnswersPage,
                    mode,
                    request.userAnswers,
                    sessionRepository
                  ) { _ =>
                    Future.successful(Ok(view(preparedForm2, items2, pageTitle2, heading2, formAction2, backUrl2)))
                  }

                case _ =>
                  // otherwise, persist a default parent and label and render
                  val labelForParent = parentLabelKeyOpt2.flatMap(k => Some(messagesApi.preferred(request)(k))).getOrElse(childToPersist2)
                  val saved = for {
                    afterSetParent      <- request.userAnswers.set(pages.PurchaseSubTypePage, childToPersist2)
                    afterSetParentLabel <- afterSetParent.set(pages.PurchaseSubTypeLabelPage, labelForParent)
                  } yield afterSetParentLabel
                  ControllerHelpers.persistAndThen(saved, sessionRepository) { ua =>
                    // When persisting a default parent in CheckMode, also
                    // mark that the user arrived from the sub-category
                    // change flow so PurchaseTypeController can route back
                    // here when appropriate. Use the already-persisted `ua`
                    // to avoid overwriting the saved default parent.
                    ControllerHelpers.markArrivalAndRender(
                      PurchaseSubCategoryArrivedFromCheckYourAnswersPage,
                      mode,
                      ua,
                      sessionRepository
                    )(_ => Future.successful(Ok(view(preparedForm2, items2, pageTitle2, heading2, formAction2, backUrl2))))
                  }
              }
            }

          case _ =>
            // missing parent or country -> recover the journey
            Future.successful(Redirect(controllers.routes.JourneyRecoveryController.onPageLoad()))
        }
      }
    }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // compute parent and country from session
    val maybeParent = request.userAnswers.get(pages.PurchaseTypePage).map(_.toString)
    val maybeCountry = utils.CountryCode.findCountryCode(request.userAnswers)

    (maybeParent, maybeCountry) match {
      case (Some(parentKey), Some(country)) =>
        // compute effective parent code from session or config
        val effectiveParentCode = effectiveParentCodeFor(country, parentKey, request.userAnswers)

        // prepare all view-related data required to validate and persist
        val (resolvedParentCode,
             options,
             items,
             pageTitle,
             heading,
             preparedForm,
             formAction,
             backUrl,
             parentBase,
             childToPersist,
             parentLabelKeyOpt
            ) =
          prepareSubCategoryViewData(parentKey, effectiveParentCode, effectiveParentCode, country, request.userAnswers, mode)(request)

        // when no options exist for this parent, redirect appropriately
        if (options.isEmpty)
          Future.successful(
            if (mode == models.CheckMode) Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
            else Redirect(routes.InvoiceTypeController.onPageLoad(mode))
          )
        else {
          // bind the POSTed form and handle validation or success
          preparedForm
            .bindFromRequest()
            .fold(
              // validation errors -> re-render with errors
              formWithErrors => Future.successful(BadRequest(view(formWithErrors, items, pageTitle, heading, formAction, backUrl))),
              // successful submission -> short-circuit or persist
              value => {
                // Short-circuit unchanged submissions in CheckMode back to the Purchase CYA
                utils.CheckModeShortCircuit.shortCircuitIfUnchanged(
                  pages.PurchaseSubCategoryPage,
                  value,
                  mode,
                  request.userAnswers,
                  controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
                ) match {
                  case Some(res) => Future.successful(res)
                  case None      =>
                    // handle the special sentinel value representing 'None'
                    if (value == ConfigPurchaseMapping.NoneValue) {
                      val noneLabel = ConfigPurchaseMapping.NoneValue
                      val savedTry = for {
                        a1 <- request.userAnswers.set(PurchaseSubCategoryPage, ConfigPurchaseMapping.NoneValue)
                        a2 <- a1.set(PurchaseSubCategoryLabelPage, noneLabel)
                      } yield a2

                      // persist and redirect based on mode
                      ControllerHelpers.persistAndThen(savedTry, sessionRepository)(ua =>
                        Future.successful(
                          if (mode == models.CheckMode) Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
                          else Redirect(routes.InvoiceTypeController.onPageLoad(mode))
                        )
                      )
                    } else {
                      // normal selection: compute label then persist
                      val labelKeyOpt = options.find(_._1 == value).map(_._2)
                      val label = labelKeyOpt.map(k => messagesApi.preferred(request)(k)).getOrElse(value)

                      val savedTry = for {
                        afterSet      <- request.userAnswers.set(PurchaseSubCategoryPage, value)
                        afterSetLabel <- afterSet.set(PurchaseSubCategoryLabelPage, label)
                      } yield afterSetLabel

                      // persist and redirect based on mode
                      ControllerHelpers.persistAndThen(savedTry, sessionRepository)(ua =>
                        Future.successful(
                          if (mode == models.CheckMode) Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
                          else Redirect(routes.InvoiceTypeController.onPageLoad(mode))
                        )
                      )
                    }
                }
              }
            )
        }

      case _ =>
        // missing context -> recover the journey
        Future.successful(Redirect(controllers.routes.JourneyRecoveryController.onPageLoad()))
    }
  }

}
