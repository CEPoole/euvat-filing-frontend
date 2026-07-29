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
import pages.{PurchaseSubTypePage, PurchaseTypePage, RefundingCountryNamePage, RefundingCountryPage, PurchaseSubTypeLabelPage, PurchaseSubCategoryPage, PurchaseSubCategoryLabelPage, CountryChangedPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Call, RequestHeader}
import repositories.SessionRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.{ConfigPurchaseMapping, MountPrefix}
import views.html.PurchaseSubTypeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
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

  private def resolveParentAndCountry(purchaseTypeSlug: String, userAnswers: UserAnswers): Option[(String, String)] = {
    val maybeParent = PurchaseType.values.find(pt => PurchaseType.slugOf(pt) == purchaseTypeSlug).map(_.toString).orElse(userAnswers.get(PurchaseTypePage).map(_.toString))
    val maybeCountry = resolveCountryCode(userAnswers)
    (maybeParent, maybeCountry) match {
      case (Some(parentKey), Some(country)) => Some((parentKey, country))
      case _                                  => None
    }
  }

  private def prepareViewData(parentKey: String, country: String, purchaseTypeSlug: String, userAnswers: UserAnswers)(implicit request: RequestHeader) = {
    val options = config.subcodesFor(country, parentKey)
    val rawItems = config.buildRadioItems(options, messagesApi.preferred(request))
    val items = if (parentKey == "other") rawItems.filterNot(_.value.contains(ConfigPurchaseMapping.NoneValue)) else rawItems
    val parentHeading = parentHeadingFor(parentKey)
    val preparedForm = userAnswers.get(PurchaseSubTypePage).fold(form)(form.fill)
    val resolvedSlug = resolvedSlugFor(parentKey, purchaseTypeSlug)
    val formAction = formActionFor(resolvedSlug)

    (options, items, parentHeading, preparedForm, resolvedSlug, formAction)
  }

  private def persistSelection(currentAnswers: UserAnswers, parentKey: String, value: String, label: String): scala.util.Try[UserAnswers] =
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

  private def formActionFor(resolvedSlug: String)(implicit request: RequestHeader) = {
    val prefix = MountPrefix.get
    val path = if (prefix.isEmpty) s"/${resolvedSlug}" else s"$prefix/${resolvedSlug}"
    Call("POST", path)
  }

  private def backUrlFor(mode: Mode) = controllers.routes.PurchaseTypeController.onPageLoad(mode).url

  // mount prefix computed with utils.MountPrefix

  private def resolveCountryCode(userAnswers: UserAnswers): Option[String] =
    userAnswers.get(RefundingCountryPage).orElse {
      userAnswers.get(RefundingCountryNamePage).map { stored =>
        val parts = stored.split(",", 2).map(_.trim)
        if (parts.length > 1) parts.last else stored
      }
    }

  def onPageLoad(purchaseTypeSlug: String, mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    if (request.userAnswers.get(CountryChangedPage).contains(true)) {
      val clearedAnswers = for {
        afterRemovedSubType      <- request.userAnswers.remove(PurchaseSubTypePage)
        afterRemovedSubTypeLabel <- afterRemovedSubType.remove(PurchaseSubTypeLabelPage)
        afterClearedFlag         <- afterRemovedSubTypeLabel.remove(pages.CountryChangedPage)
      } yield afterClearedFlag

        Future.fromTry(clearedAnswers).flatMap(updated => sessionRepository.set(updated).map { _ =>
        implicit val req: RequestHeader = request
        val prefix = MountPrefix.get
        val path = if (prefix.isEmpty) s"/${purchaseTypeSlug}" else s"$prefix/${purchaseTypeSlug}"
        Redirect(Call("GET", path))
      })
    } else {
      resolveParentAndCountry(purchaseTypeSlug, request.userAnswers) match {
        case Some((parentKey, country)) =>

          val (options, items, parentHeading, preparedForm, resolvedSlug, formAction) =
            prepareViewData(parentKey, country, purchaseTypeSlug, request.userAnswers)(request)

          if (options.isEmpty) Future.successful(Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode)))
          else {
            val backUrl = backUrlFor(mode)
            Future.successful(Ok(view(preparedForm, items, parentHeading, parentHeading, formAction, backUrl)))
          }

        case None => Future.successful(Redirect(controllers.routes.JourneyRecoveryController.onPageLoad()))
      }
    }
  }

  def onSubmit(purchaseTypeSlug: String, mode: Mode): Action[AnyContent] =
    (identify andThen getData andThen requireData).async { implicit request =>

      resolveParentAndCountry(purchaseTypeSlug, request.userAnswers) match {
        case Some((parentKey, country)) =>
          val (options, items, parentHeading, _, resolvedSlug, _) =
            prepareViewData(parentKey, country, purchaseTypeSlug, request.userAnswers)(request)

          if (options.isEmpty) {
            Future.successful(Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode)))
          } else {
            val parentHeadingVal = parentHeading

            form.bindFromRequest().fold(
              formWithErrors => {
                val formAction = formActionFor(resolvedSlug)
                val backUrl = backUrlFor(mode)
                Future.successful(BadRequest(view(formWithErrors, items, parentHeadingVal, parentHeadingVal, formAction, backUrl)))
              },
              value => {
                if (value == ConfigPurchaseMapping.NoneValue) {
                  val noneLabel = ConfigPurchaseMapping.NoneValue
                  val savedTry = for {
                    a1 <- request.userAnswers.set(PurchaseSubTypePage, ConfigPurchaseMapping.NoneValue)
                    a2 <- a1.set(PurchaseSubTypeLabelPage, noneLabel)
                    a3 <- a2.remove(PurchaseSubCategoryPage)
                    a4 <- a3.remove(PurchaseSubCategoryLabelPage)
                  } yield a4

                  for {
                    updatedAnswers <- Future.fromTry(savedTry)
                    _ <- sessionRepository.set(updatedAnswers)
                  } yield Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode))

                } else {
                  val labelKeyOpt = config.subcodesFor(country, parentKey).find(_._1 == value).map(_._2)
                  val label = labelKeyOpt.map(k => messagesApi.preferred(request)(k)).getOrElse(value)

                  val savedTry = persistSelection(request.userAnswers, parentKey, value, label)

                  for {
                    updatedAnswers <- Future.fromTry(savedTry)
                    _ <- sessionRepository.set(updatedAnswers)
                  } yield {
                    val children = config.subcategoriesFor(country, parentKey, value)

                    if (children.nonEmpty) {
                      val routeParentCodeCandidate = value
                      val candidates = Seq(routeParentCodeCandidate).distinct

                      val maybeCall = candidates.iterator.map { c =>
                        try {
                          val slug = PurchaseSubCategoryType.pathFor(parentKey, c)
                          val prefix = MountPrefix.get
                          Some(Call("GET", s"$prefix/$slug"))
                        } catch {
                          case _: Throwable => None
                        }
                      }.collectFirst { case Some(call) => call }

                      maybeCall match {
                        case Some(call) => Redirect(call)
                        case None       => Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode))
                      }

                    } else {
                      val lastSeg = value.split("\\.").lastOption.getOrElse(value)
                      val isOtherPurchaseType = PurchaseType.values.find(pt => PurchaseType.slugOf(pt) == resolvedSlug).contains(PurchaseType.Other)

                      if (isOtherPurchaseType && lastSeg == "99")
                        Redirect(controllers.routes.DescribeItemsOnInvoiceController.onPageLoad(mode))
                      else
                        Redirect(controllers.routes.InvoiceTypeController.onPageLoad(mode))
                    }
                  }
                }
              }
            )
          }

        case None => Future.successful(Redirect(controllers.routes.JourneyRecoveryController.onPageLoad()))
      }
  }

}
