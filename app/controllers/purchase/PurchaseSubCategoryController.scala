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
import pages.{CountryChangedPage, PurchaseSubCategoryLabelPage, PurchaseSubCategoryPage, PurchaseSubTypeLabelPage, PurchaseSubTypePage, PurchaseTypePage}
import play.api.i18n.{I18nSupport, MessagesApi}
import models.PurchaseType
import models.PurchaseSubCategoryType
import models.{Mode, UserAnswers}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import controllers.routes
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

  private def stripLeadingNumeric(key: String): String = {
    val parts = key.split("\\.")
    if (parts.length >= 5 && parts.head == "purchase" && parts(1) == "sub") (parts.take(3) ++ parts.drop(4)).mkString(".")
    else key
  }

  private def titleForLabelKey(labelKey: String, msgs: play.api.i18n.Messages): Option[String] = {
    val original = s"$labelKey.title"
    val stripped = s"${stripLeadingNumeric(labelKey)}.title"
    Seq(original, stripped).collectFirst { case k if msgs.isDefinedAt(k) => msgs(k) }
  }

  private def parentDerivedTitle(parentKey: String, resolvedParentCode: String, msgs: play.api.i18n.Messages): Option[String] = {
    val asIs = s"purchase.sub.$parentKey.$resolvedParentCode.title"
    val dropLeading = {
      val parts = resolvedParentCode.split("\\.")
      if (parts.length > 1) s"purchase.sub.$parentKey.${parts.drop(1).mkString(".")}.title" else asIs
    }
    val lastSeg = resolvedParentCode.split("\\.").lastOption.map(s => s"purchase.sub.$parentKey.$s.title").getOrElse(asIs)
    Seq(asIs, dropLeading, lastSeg).collectFirst { case k if msgs.isDefinedAt(k) => msgs(k) }
  }

  private def tryReverseParent(parentKey: String, candidate: String)(implicit request: play.api.mvc.RequestHeader): Option[Call] = {
    try {
      val slug = PurchaseSubCategoryType.pathFor(parentKey, candidate)
      val prefix = utils.MountPrefix.get
      val url = if (prefix.isEmpty) s"/$slug" else s"$prefix/$slug"
      Some(Call("POST", url))
    } catch { case _: Throwable => None }
  }

  private def computeFormAction(parentKey: String, candidates: Seq[String], userAnswers: UserAnswers)(implicit
    request: play.api.mvc.RequestHeader
  ): Call = {
    val prefix = utils.MountPrefix.get
    val maybeSessionSlug = userAnswers.get(PurchaseTypePage).map(models.PurchaseType.slugOf)
    candidates.iterator
      .flatMap(c => tryReverseParent(parentKey, c))
      .find(_ => true)
      .getOrElse(
        maybeSessionSlug
          .map(slug => Call("POST", if (prefix.isEmpty) s"/$slug" else s"$prefix/$slug"))
          .getOrElse(Call("POST", if (prefix.isEmpty) s"/" else s"$prefix/"))
      )
  }

  private def backUrlFor(userAnswers: UserAnswers)(implicit request: play.api.mvc.RequestHeader): String = {
    val prefix = utils.MountPrefix.get
    userAnswers.get(PurchaseTypePage).map(models.PurchaseType.slugOf) match {
      case Some(slug) =>
        val url = if (prefix.isEmpty) s"/$slug" else s"$prefix/$slug"
        Call("GET", url).url
      case None => controllers.routes.PurchaseTypeController.onPageLoad(models.NormalMode).url
    }
  }

  // mount prefix computed with utils.MountPrefix

  private def findByLastSegment(parentKey: String, seg: String, country: String): Option[String] =
    config.subcodesFor(country, parentKey).map(_._1).find(code => code.split("\\.").lastOption.contains(seg))

  private def computeResolvedParentAndOptions(parentKey: String,
                                              effectiveParentCode: String,
                                              parentCode: String,
                                              country: String
                                             ): (String, Seq[(String, String)]) = {
    val initialOptions = config.subcategoriesFor(country, parentKey, effectiveParentCode)
    if (initialOptions.nonEmpty) (effectiveParentCode, initialOptions)
    else {
      val alt = effectiveParentCode.split("\\.").drop(1).mkString(".")
      val altOptions = if (alt.nonEmpty) config.subcategoriesFor(country, parentKey, alt) else Seq.empty
      if (altOptions.nonEmpty) (alt, altOptions)
      else
        findByLastSegment(parentKey, parentCode, country)
          .map(found => (found, config.subcategoriesFor(country, parentKey, found)))
          .getOrElse((parentCode, initialOptions))
    }
  }

  private def prepareSubCategoryViewData(parentKey: String,
                                         parentCode: String,
                                         effectiveParentCode: String,
                                         country: String,
                                         userAnswers: UserAnswers
                                        )(implicit request: play.api.mvc.RequestHeader) = {
    val msgs = messagesApi.preferred(request)

    val (resolvedParentCode, options) = computeResolvedParentAndOptions(parentKey, effectiveParentCode, parentCode, country)

    val items = config.buildRadioItems(options, msgs)

    val lastSeg = resolvedParentCode.split("\\.").lastOption.getOrElse(resolvedParentCode)
    val headSeg = resolvedParentCode.split("\\.").headOption.getOrElse(resolvedParentCode)

    // Prefer a child title specifically for the last-segment (e.g. "5" for "3.5"),
    // then the fully-qualified resolvedParentCode, then the head segment. Fall back
    // to any title discovered from the option label keys.
    val specificTitleKeys = Seq(
      s"purchase.sub.$parentKey.$lastSeg.title",
      s"purchase.sub.$parentKey.$resolvedParentCode.title",
      s"purchase.sub.$parentKey.$headSeg.title"
    )

    val childTitleOpt = specificTitleKeys
      .collectFirst { case k if msgs.isDefinedAt(k) => msgs(k) }
      .orElse(options.to(LazyList).flatMap { case (_, labelKey) => titleForLabelKey(labelKey, msgs) }.headOption)

    val parentLabelKeyOpt = config.subcodesFor(country, parentKey).find(_._1 == resolvedParentCode).map(_._2)
    val parentHeading = msgs(s"purchase.sub.$parentKey.heading")
    val heading = childTitleOpt.orElse(parentDerivedTitle(parentKey, resolvedParentCode, msgs)).getOrElse(parentHeading)
    val pageTitle = heading

    // Resolve a context-specific error key for missing selection. Prefer an
    // error keyed to the last-segment (e.g. "1" for "3.1") and then fall
    // back to the parent-level key. Avoid using the head segment or the
    // fully-qualified resolved code here to prevent mismatches.
    val candidateKeys = Seq(
      s"purchase.sub.$parentKey.$lastSeg.error.required",
      s"purchase.sub.$parentKey.error.required"
    )
    val requiredKey = candidateKeys.find(k => msgs.isDefinedAt(k)).getOrElse("error.required")
    val preparedForm = userAnswers.get(PurchaseSubCategoryPage).fold(formProvider(requiredKey))(formProvider(requiredKey).fill)

    val head = resolvedParentCode.split("\\.").headOption.getOrElse(resolvedParentCode)
    val last = resolvedParentCode.split("\\.").lastOption.getOrElse(resolvedParentCode)
    val candidates = Seq(resolvedParentCode, last, head).distinct

    val formAction = computeFormAction(parentKey, candidates, userAnswers)(request)
    val backUrl = backUrlFor(userAnswers)

    val parentBase = resolvedParentCode.split("\\.").headOption.getOrElse(resolvedParentCode)

    val childToPersist = if (resolvedParentCode.contains(".")) resolvedParentCode else options.headOption.map(_._1).getOrElse(resolvedParentCode)

    (resolvedParentCode, options, items, pageTitle, heading, preparedForm, formAction, backUrl, parentBase, childToPersist, parentLabelKeyOpt)
  }

  private def resolveCountryCode(userAnswers: UserAnswers): Option[String] =
    userAnswers.get(pages.RefundingCountryPage).orElse {
      userAnswers.get(pages.RefundingCountryNamePage).map { stored =>
        val parts = stored.split(",", 2).map(_.trim)
        if (parts.length > 1) parts.last else stored
      }
    }

  def onPageLoad(mode: Mode): Action[AnyContent] =
    (identify andThen getData andThen requireData).async { implicit request =>

      if (request.userAnswers.get(pages.CountryChangedPage).contains(true)) {
        val clearedAnswers = for {
          afterRemovedSubCategory      <- request.userAnswers.remove(PurchaseSubCategoryPage)
          afterRemovedSubCategoryLabel <- afterRemovedSubCategory.remove(PurchaseSubCategoryLabelPage)
          afterClearedFlag             <- afterRemovedSubCategoryLabel.remove(pages.CountryChangedPage)
        } yield afterClearedFlag

        Future.fromTry(clearedAnswers).flatMap { updated =>
          sessionRepository.set(updated).map(_ => Redirect(Call("GET", request.path)))
        }

      } else {
        val maybeParent = request.userAnswers.get(pages.PurchaseTypePage).map(_.toString)
        val maybeCountry = resolveCountryCode(request.userAnswers)

        (maybeParent, maybeCountry) match {
          case (Some(parentKey), Some(country)) =>
            val parentFromSessionOpt = request.userAnswers.get(pages.PurchaseSubTypePage)
            val effectiveParentCode = parentFromSessionOpt.getOrElse {
              // fallback to first configured subcode for this parentKey when session missing
              config.subcodesFor(country, parentKey).headOption.map(_._1).getOrElse("")
            }

            val (resolvedParentCode, options) = computeResolvedParentAndOptions(parentKey, effectiveParentCode, effectiveParentCode, country)

            if (options.isEmpty) Future.successful(Redirect(routes.InvoiceTypeController.onPageLoad(mode)))
            else {
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
                prepareSubCategoryViewData(parentKey, effectiveParentCode, effectiveParentCode, country, request.userAnswers)(request)

              request.userAnswers.get(pages.PurchaseSubTypePage) match {
                case Some(existing) if existing.split("\\.").headOption.contains(parentBase2) =>
                  Future.successful(Ok(view(preparedForm2, items2, pageTitle2, heading2, formAction2, backUrl2)))

                case _ =>
                  val labelForParent = parentLabelKeyOpt2.flatMap(k => Some(messagesApi.preferred(request)(k))).getOrElse(childToPersist2)
                  val saved = for {
                    afterSetParent      <- request.userAnswers.set(pages.PurchaseSubTypePage, childToPersist2)
                    afterSetParentLabel <- afterSetParent.set(pages.PurchaseSubTypeLabelPage, labelForParent)
                  } yield afterSetParentLabel

                  Future.fromTry(saved).flatMap { finalAnswers =>
                    sessionRepository.set(finalAnswers).map(_ => Ok(view(preparedForm2, items2, pageTitle2, heading2, formAction2, backUrl2)))
                  }
              }
            }

          case _ => Future.successful(Redirect(controllers.routes.JourneyRecoveryController.onPageLoad()))
        }
      }
    }

  def onSubmit(mode: Mode): Action[AnyContent] = (identify andThen getData andThen requireData).async { implicit request =>
    val maybeParent = request.userAnswers.get(pages.PurchaseTypePage).map(_.toString)
    val maybeCountry = resolveCountryCode(request.userAnswers)

    (maybeParent, maybeCountry) match {
      case (Some(parentKey), Some(country)) =>
        val parentFromSessionOpt = request.userAnswers.get(pages.PurchaseSubTypePage)
        val effectiveParentCode = parentFromSessionOpt.getOrElse {
          config.subcodesFor(country, parentKey).headOption.map(_._1).getOrElse("")
        }

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
          prepareSubCategoryViewData(parentKey, effectiveParentCode, effectiveParentCode, country, request.userAnswers)(request)

        if (options.isEmpty) Future.successful(Redirect(routes.InvoiceTypeController.onPageLoad(mode)))
        else {
          preparedForm
            .bindFromRequest()
            .fold(
              formWithErrors => Future.successful(BadRequest(view(formWithErrors, items, pageTitle, heading, formAction, backUrl))),
              value => {
                if (value == ConfigPurchaseMapping.NoneValue) {
                  val noneLabel = ConfigPurchaseMapping.NoneValue
                  val savedTry = for {
                    a1 <- request.userAnswers.set(PurchaseSubCategoryPage, ConfigPurchaseMapping.NoneValue)
                    a2 <- a1.set(PurchaseSubCategoryLabelPage, noneLabel)
                  } yield a2

                  val fut = for {
                    updated <- Future.fromTry(savedTry)
                    _       <- sessionRepository.set(updated)
                  } yield Redirect(routes.InvoiceTypeController.onPageLoad(mode))

                  fut
                } else {
                  val labelKeyOpt = options.find(_._1 == value).map(_._2)
                  val label = labelKeyOpt.map(k => messagesApi.preferred(request)(k)).getOrElse(value)

                  val savedTry = for {
                    afterSet      <- request.userAnswers.set(PurchaseSubCategoryPage, value)
                    afterSetLabel <- afterSet.set(PurchaseSubCategoryLabelPage, label)
                  } yield afterSetLabel

                  for {
                    updated <- Future.fromTry(savedTry)
                    _       <- sessionRepository.set(updated)
                  } yield Redirect(routes.InvoiceTypeController.onPageLoad(mode))
                }
              }
            )
        }

      case _ => Future.successful(Redirect(controllers.routes.JourneyRecoveryController.onPageLoad()))
    }
  }

}
