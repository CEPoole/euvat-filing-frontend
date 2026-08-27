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
import forms.PurchaseSubTypeFormProvider
import navigation.Navigator
import pages.{CountryChangedPage, PurchaseSubCategoryLabelPage, PurchaseSubCategoryPage, PurchaseSubTypeArrivedFromCheckYourAnswersPage, PurchaseSubTypeLabelPage, PurchaseSubTypePage, PurchaseTypePage, RefundingCountryNamePage, RefundingCountryPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents, Request, RequestHeader}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{ConfigPurchaseMapping, ControllerHelpers, CountryCode, MountPrefix}
import views.html.PurchaseSubTypeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import models.{Mode, Other, PurchaseSubCategoryType, PurchaseType, UserAnswers}

class PurchaseSubTypeController @Inject() (
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
    with play.api.Logging:

  private def resolveParentAndCountry(purchaseTypeSlug: String, userAnswers: UserAnswers): Option[(String, String)] =
    // Attempt to determine `parentKey` from the provided slug first
    val parentKey =
      PurchaseType.valueFromUrlSlug
        .get(purchaseTypeSlug)
        .orElse(
          userAnswers
            .get(PurchaseTypePage)
            .map(_.toString)
        )
    // Attempt to determine the refunding country from `UserAnswers`
    val country = CountryCode.findCountryCode(userAnswers)

    (parentKey, country) match {
      case (Some(parentKey), Some(country)) => Some((parentKey, country))
      case _                                => None
    }

  private def prepareViewData(parentKey: String, country: String, purchaseTypeSlug: String, userAnswers: UserAnswers, mode: Mode)(implicit
    request: RequestHeader
  ) = {
    // Fetch configured options for this parent/country combination
    val options = config.subcodesFor(country, parentKey)

    // Build radio list items from config and the request's preferred messages
    val rawItems = config.buildRadioItems(options, messagesApi.preferred(request))

    // For the `other` parent we exclude the sentinel 'None' option from the list
    val items = if (parentKey == "other") rawItems.filterNot(_.value.contains(ConfigPurchaseMapping.NoneValue)) else rawItems

    // Resolve a human-friendly heading for the parent section
    val parentHeading = parentHeadingFor(parentKey)

    // Preferred messages helper for lookups
    val msgs = messagesApi.preferred(request)

    // Pick a validation error key scoped to the parent when available
    val requiredKeyCandidates = Seq(s"purchase.sub.$parentKey.error.required")
    val requiredKey = requiredKeyCandidates.find(k => msgs.isDefinedAt(k)).getOrElse("error.required")

    // Prepare the form: fill with an existing answer when present
    val preparedForm = userAnswers.get(PurchaseSubTypePage).fold(formProvider(requiredKey))(formProvider(requiredKey).fill)

    // Compute the route slug used for the form action
    val resolvedSlug = resolvedSlugFor(parentKey, purchaseTypeSlug)
    val formAction = formActionFor(resolvedSlug, mode)

    // Return the view data tuple consumed by controller actions
    (options, items, parentHeading, preparedForm, resolvedSlug, formAction)
  }

  private def persistSelection(currentAnswers: UserAnswers, parentKey: String, value: String, label: String): scala.util.Try[UserAnswers] =
    currentAnswers.get(PurchaseSubTypePage) match {
      case Some(previousSelection) if previousSelection != value =>
        // When an existing different selection is present we must clear
        // dependent sub-category fields before setting the new subtype.
        for {
          removedSubCategory      <- currentAnswers.remove(PurchaseSubCategoryPage)
          removedSubCategoryLabel <- removedSubCategory.remove(PurchaseSubCategoryLabelPage)
          setSubType              <- removedSubCategoryLabel.set(PurchaseSubTypePage, value)
          setSubTypeLabel         <- setSubType.set(PurchaseSubTypeLabelPage, label)
          // Ensure PurchaseType is set in session when missing
          finalAnswers <- currentAnswers.get(PurchaseTypePage) match {
                            case Some(_) => scala.util.Success(setSubTypeLabel)
                            case None =>
                              PurchaseType.values.find(_.toString == parentKey) match {
                                case Some(pt) => setSubTypeLabel.set(PurchaseTypePage, pt)
                                case None     => scala.util.Success(setSubTypeLabel)
                              }
                          }
        } yield finalAnswers

      case _ =>
        // No previous selection or it is unchanged: set subtype and label
        for {
          setSubType      <- currentAnswers.set(PurchaseSubTypePage, value)
          setSubTypeLabel <- setSubType.set(PurchaseSubTypeLabelPage, label)
          // Ensure PurchaseType exists in session when missing
          finalAnswers <- currentAnswers.get(PurchaseTypePage) match {
                            case Some(_) => scala.util.Success(setSubTypeLabel)
                            case None =>
                              PurchaseType.values.find(_.toString == parentKey) match {
                                case Some(pt) => setSubTypeLabel.set(PurchaseTypePage, pt)
                                case None     => scala.util.Success(setSubTypeLabel)
                              }
                          }
        } yield finalAnswers
    }

  private def parentHeadingFor(parentKey: String)(implicit request: play.api.mvc.RequestHeader): String =
    // Map known parent keys to their localized headings; fallback to key
    parentKey match {
      case "fuel"         => messagesApi.preferred(request)("purchase.sub.fuel.heading")
      case "transport"    => messagesApi.preferred(request)("purchase.sub.transport.heading")
      case "foodAndDrink" => messagesApi.preferred(request)("purchase.sub.foodAndDrink.heading")
      case "luxuries"     => messagesApi.preferred(request)("purchase.sub.luxuries.heading")
      case "other"        => messagesApi.preferred(request)("purchase.sub.other.heading")
      case _              => parentKey
    }

  private def resolvedSlugFor(parentKey: String, fallback: String): String =
    // Derive a URL slug for routing from the PurchaseType enum or fallback
    PurchaseType.values.find(_.toString == parentKey).map(PurchaseType.urlSlugForPurchaseType).getOrElse(fallback)

  private def formActionFor(uri: String, mode: Mode)(implicit request: RequestHeader) = {
    // Compute POST action URL slug respecting mount prefix and CheckMode change- prefix
    val isChangeMode = if (mode == models.CheckMode) "change-" else ""
    Call("POST", s"${MountPrefix.getFromRequest}/$isChangeMode$uri")
  }

  private def backUrlFor(mode: Mode) = controllers.routes.PurchaseTypeController.onPageLoad(mode).url

  // Handle the case where the country has changed in session: clear dependent
  // subtype/subcategory fields and persist the cleared answers, then redirect
  // back to the same purchase type path.
  private def handleCountryChanged(userAnswers: UserAnswers)(implicit request: RequestHeader) =
    // Clear subtype and subtype label, then remove the CountryChanged flag
    val updatedAnswers = for
      afterRemovedSubType      <- userAnswers.remove(PurchaseSubTypePage)
      afterRemovedSubTypeLabel <- afterRemovedSubType.remove(PurchaseSubTypeLabelPage)
      afterClearedFlag         <- afterRemovedSubTypeLabel.remove(pages.CountryChangedPage)
    yield afterClearedFlag
    Future.fromTry(updatedAnswers).map(sessionRepository.set)

  // Render the standard sub-type selection view given prepared view data
  private def renderSubTypeView(preparedForm: play.api.data.Form[?],
                                items: Seq[uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem],
                                heading: String,
                                formAction: Call,
                                mode: Mode
                               )(implicit request: Request[AnyContent]): Future[play.api.mvc.Result] = {
    val backUrl = backUrlFor(mode)
    Future.successful(Ok(view(preparedForm, items, heading, heading, formAction, backUrl)))
  }

  def onPageLoad(purchaseTypeSlug: String, mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async:
    implicit request =>
      // If the country flag indicates a change, clear dependent fields
      if request.userAnswers.get(CountryChangedPage).contains(true) then
        handleCountryChanged(request.userAnswers)
          .map(_ => Redirect(Call("GET", s"${MountPrefix.getFromRequest}/$purchaseTypeSlug")))
      else
        // Attempt to resolve the parentKey and country from session/slug
        resolveParentAndCountry(purchaseTypeSlug, request.userAnswers) match
          case None                       => Future.successful(Redirect(controllers.routes.JourneyRecoveryController.onPageLoad()))
          case Some((parentKey, country)) =>
            // Prepare all view data required to render the radio list
            val (options, items, parentHeading, preparedForm, resolvedSlug, formAction) =
              prepareViewData(parentKey, country, purchaseTypeSlug, request.userAnswers, mode)(request)

            // If no options exist for this parent, route to InvoiceType (or CYA in CheckMode)
            if options.isEmpty then
              Future.successful(
                if mode == models.CheckMode
                then Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
                else Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode))
              )
            else {
              lazy val singleCode = options.head._1
              lazy val lastSeg = singleCode.split("\\.").lastOption.getOrElse(singleCode)
              // Special-case: `other` parent with a single sentinel '99' option
              if (parentKey == "other" && options.size == 1 && lastSeg == "99") {
                // Persist the single sentinel value and redirect straight to description
                val labelKey = options.head._2
                val label = if (labelKey != null && labelKey.nonEmpty) messagesApi.preferred(request)(labelKey) else singleCode
                val savedTry = persistSelection(request.userAnswers, parentKey, singleCode, label)

                ControllerHelpers.persistAndThen(savedTry, sessionRepository) { _ =>
                  Future.successful(Redirect(controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(mode)))
                }
              } else {
                // Standard rendering path: show the radio list
                ControllerHelpers.markArrivalAndRender(
                  pages.PurchaseSubTypeArrivedFromCheckYourAnswersPage,
                  mode,
                  request.userAnswers,
                  sessionRepository
                )(_ => renderSubTypeView(preparedForm, items, parentHeading, formAction, mode))
              }
            }

  def onSubmit(purchaseTypeSlug: String, mode: Mode): Action[AnyContent] =
    (identify andThen getData andThen requireData).async { implicit request =>
      // Resolve context (parentKey + country) from slug/session
      resolveParentAndCountry(purchaseTypeSlug, request.userAnswers) match {
        case Some((parentKey, country)) =>
          // Prepare view data to enable validation and label lookups
          val (options, items, parentHeading, preparedForm, resolvedSlug, _) =
            prepareViewData(parentKey, country, purchaseTypeSlug, request.userAnswers, mode)(request)

          if (options.isEmpty)
            Future.successful(
              if mode == models.CheckMode
              then Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
              else Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode))
            )
          else {
            // Bind and validate the submitted form
            preparedForm
              .bindFromRequest()
              .fold(
                // Validation errors -> re-render form with errors
                formWithErrors => {
                  val formAction = formActionFor(resolvedSlug, mode)
                  val backUrl = backUrlFor(mode)
                  Future.successful(BadRequest(view(formWithErrors, items, parentHeading, parentHeading, formAction, backUrl)))
                },
                // Valid submission -> short-circuit or persist and navigate
                value => {
                  // If unchanged in CheckMode, short-circuit back to CYA
                  utils.CheckModeShortCircuit.shortCircuitIfUnchanged(
                    pages.PurchaseSubTypePage,
                    value,
                    mode,
                    request.userAnswers,
                    controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
                  ) match {
                    case Some(res) => Future.successful(res)
                    case None      =>
                      // Handle the sentinel 'None' value specially: clear subcategory
                      if (value == ConfigPurchaseMapping.NoneValue) {
                        val noneLabel = ConfigPurchaseMapping.NoneValue
                        val savedTry = for {
                          a1 <- request.userAnswers.set(PurchaseSubTypePage, ConfigPurchaseMapping.NoneValue)
                          a2 <- a1.set(PurchaseSubTypeLabelPage, noneLabel)
                          a3 <- a2.remove(PurchaseSubCategoryPage)
                          a4 <- a3.remove(PurchaseSubCategoryLabelPage)
                        } yield a4

                        ControllerHelpers.persistAndThen(savedTry, sessionRepository) { _ =>
                          Future.successful(
                            if (mode == models.CheckMode) Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
                            else Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode))
                          )
                        }

                      } else {
                        // Normal selection: resolve label and persist changes
                        val labelKeyOpt = config.subcodesFor(country, parentKey).find(_._1 == value).map(_._2)
                        val label = labelKeyOpt.map(k => messagesApi.preferred(request)(k)).getOrElse(value)

                        val savedTry = persistSelection(request.userAnswers, parentKey, value, label)

                        ControllerHelpers.persistAndThen(savedTry, sessionRepository) { _ =>
                          // After persisting decide whether to navigate to subcategory
                          val children = config.subcategoriesFor(country, parentKey, value)

                          if (children.nonEmpty) {
                            // Attempt to compute a route to the sub-category page
                            val routeParentCodeCandidate = value
                            val candidates = Seq(routeParentCodeCandidate).distinct

                            val maybeCall = candidates.iterator
                              .map { c =>
                                try {
                                  val slug = PurchaseSubCategoryType.pathFor(parentKey, c)
                                  val prefix = MountPrefix.getFromRequest
                                  val path =
                                    if (mode == models.CheckMode)
                                      // in CheckMode use change- prefixed paths so the
                                      // user returns to the edit (change-*) route
                                      if (prefix.isEmpty) s"/change-$slug" else s"$prefix/change-$slug"
                                    else if (prefix.isEmpty) s"/$slug"
                                    else s"$prefix/$slug"
                                  Some(Call("GET", path))
                                } catch {
                                  case _: Throwable => None
                                }
                              }
                              .collectFirst { case Some(call) => call }
                            Future.successful(maybeCall.fold(Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode)))(Redirect))
                          } else {
                            // No children present: special-case `other` + sentinel '99'
                            val lastSeg = value.split("\\.").lastOption.getOrElse(value)
                            val isOtherPurchaseType =
                              PurchaseType.values.find(pt => PurchaseType.urlSlugForPurchaseType(pt) == resolvedSlug).contains(Other)

                            Future.successful(
                              if (isOtherPurchaseType && lastSeg == "99")
                                Redirect(controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(mode))
                              else if (mode == models.CheckMode) Redirect(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad())
                              else Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode))
                            )
                          }
                        }
                      }
                  }
                }
              )
          }

        case None => Future.successful(Redirect(controllers.routes.JourneyRecoveryController.onPageLoad()))
      }
    }
