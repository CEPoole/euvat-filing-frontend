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
import pages.{PurchaseSubTypePage, PurchaseTypePage, RefundingCountryNamePage, RefundingCountryPage, PurchaseSubTypeLabelPage, PurchaseSubCategoryPage, PurchaseSubCategoryLabelPage, CountryChangedPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.ConfigPurchaseMapping
import views.html.PurchaseSubTypeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc.Call
import models.PurchaseSubCategoryType
import models.PurchaseType
import models.{Mode, UserAnswers}
import scala.util.Try

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
    with play.api.Logging {

  val form = formProvider()

  // Helpers to reduce duplication between onPageLoad and onSubmit
  // resolveParentAndCountry:
  //  - Given a `purchaseTypeSlug` (friendly slug from the route) and the current
  //    `UserAnswers`, determine the canonical `parentKey` (internal enum key) and
  //    the resolved country code to use when looking up purchase mappings.
  //  - Returns `Some((parentKey, country))` when both are available, otherwise
  //    `None` to indicate the request cannot proceed and should be redirected
  //    to the Journey Recovery flow.
  private def resolveParentAndCountry(purchaseTypeSlug: String, userAnswers: UserAnswers): Option[(String, String)] = {
    val maybeParent = PurchaseType.fromSlug(purchaseTypeSlug).map(_.toString).orElse(userAnswers.get(PurchaseTypePage).map(_.toString))
    val maybeCountry = resolveCountryCode(userAnswers)
    (maybeParent, maybeCountry) match {
      case (Some(parentKey), Some(country)) => Some((parentKey, country))
      case _                                  => None
    }
  }

  private def prepareViewData(parentKey: String, country: String, purchaseTypeSlug: String, userAnswers: UserAnswers)(implicit request: play.api.mvc.RequestHeader) = {
    // prepareViewData:
    //  - Centralizes construction of the view data used by both `onPageLoad` and
    //    the error branch of `onSubmit` so both render the same form, labels,
    //    and radio options.
    val options = config.subcodesFor(country, parentKey)
    val items = config.buildRadioItems(options, messagesApi.preferred(request))
    val parentHeading = parentHeadingFor(parentKey)
    val preparedForm = userAnswers.get(PurchaseSubTypePage).fold(form)(form.fill)
    val resolvedSlug = resolvedSlugFor(parentKey, purchaseTypeSlug)
    val formAction = formActionFor(resolvedSlug)

    (options, items, parentHeading, preparedForm, resolvedSlug, formAction)
  }

  private def persistSelection(currentAnswers: UserAnswers, parentKey: String, value: String, label: String): scala.util.Try[UserAnswers] =
    // persistSelection:
    //  - Persist the selected `value` and human-friendly `label` into the
    //    `UserAnswers`. If the selection changed, clear any dependent
    //    `PurchaseSubCategory` and its label. Also ensure `PurchaseTypePage` is
    //    set (derived from `parentKey`) so downstream pages can rely on it.
    currentAnswers.get(PurchaseSubTypePage) match {
      case Some(previousSelection) if previousSelection != value =>
        for {
          removedSubCategory      <- currentAnswers.remove(PurchaseSubCategoryPage)
          removedSubCategoryLabel <- removedSubCategory.remove(PurchaseSubCategoryLabelPage)
          setSubType              <- removedSubCategoryLabel.set(PurchaseSubTypePage, value)
          setSubTypeLabel         <- setSubType.set(PurchaseSubTypeLabelPage, label)
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
        for {
          setSubType      <- currentAnswers.set(PurchaseSubTypePage, value)
          setSubTypeLabel <- setSubType.set(PurchaseSubTypeLabelPage, label)
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
    parentKey match {
      case "fuel"         => messagesApi.preferred(request)("purchase.sub.fuel.heading")
      case "transport"    => messagesApi.preferred(request)("purchase.sub.transport.heading")
      case "foodAndDrink" => messagesApi.preferred(request)("purchase.sub.foodAndDrink.heading")
      case "luxuries"     => messagesApi.preferred(request)("purchase.sub.luxuries.heading")
      case "other"        => messagesApi.preferred(request)("purchase.sub.other.heading")
      case _               => parentKey
    }

  private def resolvedSlugFor(parentKey: String, fallback: String): String =
    PurchaseType.values.find(_.toString == parentKey).map(PurchaseType.slugOf).getOrElse(fallback)

  private def formActionFor(resolvedSlug: String) = play.api.mvc.Call("POST", s"/$resolvedSlug")

  private def backUrlFor(mode: Mode) = routes.PurchaseTypeController.onPageLoad(mode).url

  private def resolveCountryCode(userAnswers: UserAnswers): Option[String] =
    userAnswers.get(RefundingCountryPage).orElse {
      userAnswers.get(RefundingCountryNamePage).map { stored =>
        // `RefundingCountryNamePage` may be stored as "CODE,Name" or "Name,CODE".
        // Prefer the token after the comma when present since it is often the
        // ISO code; fall back to the whole stored value otherwise.
        val parts = stored.split(",", 2).map(_.trim)
        if (parts.length > 1) parts.last else stored
      }
    }

  def onPageLoad(purchaseTypeSlug: String, mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // If the country was changed we must clear any previously stored
    // `PurchaseSubType` and its label so the user is shown the correct set of
    // options for the newly selected country.
    if (request.userAnswers.get(pages.CountryChangedPage).contains(true)) {
      val clearedAnswers = for {
        afterRemovedSubType      <- request.userAnswers.remove(PurchaseSubTypePage)
        afterRemovedSubTypeLabel <- afterRemovedSubType.remove(PurchaseSubTypeLabelPage)
        afterClearedFlag         <- afterRemovedSubTypeLabel.remove(pages.CountryChangedPage)
      } yield afterClearedFlag

      // Persist the cleared answers and redirect back to the slug route so the
      // page reloads with the country-specific options.
      Future.fromTry(clearedAnswers).flatMap(updated => sessionRepository.set(updated).map(_ => Redirect(play.api.mvc.Call("GET", s"/${purchaseTypeSlug}"))))
    } else {
      // Resolve canonical parent key and the country code used for lookups.
      resolveParentAndCountry(purchaseTypeSlug, request.userAnswers) match {
        case Some((parentKey, country)) =>

          val (options, items, parentHeading, preparedForm, resolvedSlug, formAction) =
            prepareViewData(parentKey, country, purchaseTypeSlug, request.userAnswers)(request)

          if (options.isEmpty) Future.successful(Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode)))
          else {
            val backUrl = backUrlFor(mode)
            Future.successful(Ok(view(preparedForm, items, parentHeading, parentHeading, formAction, backUrl)))
          }

        case None => Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
      }
    }
  }

  def onSubmit(purchaseTypeSlug: String, mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    // Handle form submission: validate, persist selection and navigate.
    // Steps:
    //  1. Resolve parent/country
    //  2. Validate form
    //  3. Persist selection (clearing dependent data if changed)
    //  4. If children exist, route to first child-friendly-slug; otherwise
    //     route to DescribeItemsOnInvoice (Other+99) or InvoiceType
    resolveParentAndCountry(purchaseTypeSlug, request.userAnswers) match {
      case Some((parentKey, country)) =>
        val (options, items, parentHeading, _, resolvedSlug, _) =
          prepareViewData(parentKey, country, purchaseTypeSlug, request.userAnswers)(request)

        if (options.isEmpty) Future.successful(Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode)))
        else {
          val parentHeadingVal = parentHeading

          form
            .bindFromRequest()
            .fold(
              formWithErrors => {
                val formAction = formActionFor(resolvedSlug)
                val backUrl = backUrlFor(mode)
                Future.successful(BadRequest(view(formWithErrors, items, parentHeadingVal, parentHeadingVal, formAction, backUrl)))
              },
              value => {
                val labelKeyOpt = config.subcodesFor(country, parentKey).find(_._1 == value).map(_._2)
                val label = labelKeyOpt.map(k => messagesApi.preferred(request)(k)).getOrElse(value)

                // Persist selection and store updated answers in session
                val savedTry = persistSelection(request.userAnswers, parentKey, value, label)

                for {
                  updatedAnswers <- Future.fromTry(savedTry)
                  _              <- sessionRepository.set(updatedAnswers)
                } yield {
                  val children = config.subcategoriesFor(country, parentKey, value)

                  if (children.nonEmpty) {
                    // Determine a safe parent candidate to route to.
                    val routeParentCodeCandidate = value
                    val candidates = Seq(routeParentCodeCandidate).distinct

                    val maybeCall = candidates.iterator.map { c =>
                      try {
                        val slug = PurchaseSubCategoryType.pathFor(parentKey, c)
                        val prefix = request.path.lastIndexOf('/') match {
                          case i if i > 0 => request.path.substring(0, i)
                          case _           => ""
                        }
                        Some(play.api.mvc.Call("GET", s"$prefix/$slug"))
                      } catch {
                        case _: Throwable => None
                      }
                    }.collectFirst { case Some(call) => call }

                    maybeCall match {
                      case Some(call) => Redirect(call)
                      case None       => Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode))
                    }
                  } else {
                    // No child subcategories exist. If this is the `Other` purchase type and
                    // the selected sub-type is the 'none of these / give more details' code
                    // (commonly `99` as the last segment) then route to the Describe Items
                    // on Invoice page so the user can provide free-text details.
                    val lastSeg = value.split("\\.").lastOption.getOrElse(value)
                    val isOtherPurchaseType = PurchaseType.fromSlug(resolvedSlug).contains(PurchaseType.Other)

                    if (isOtherPurchaseType && lastSeg == "99") Redirect(controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(mode))
                    else Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode))
                  }
                }
              }
            )
        }

      case None => Future.successful(Redirect(routes.JourneyRecoveryController.onPageLoad()))
    }
  }
}
