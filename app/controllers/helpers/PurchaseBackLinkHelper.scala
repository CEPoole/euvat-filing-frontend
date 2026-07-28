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

package controllers.helpers

import models.requests.DataRequest
import models.{Mode, PurchaseType}
import models.PurchaseSubCategoryType
import pages.{PurchaseTypePage, PurchaseSubTypePage, PurchaseSubCategoryPage}
import play.api.mvc.Call
import utils.MountPrefix

object PurchaseBackLinkHelper {

  def computeBackTarget(mode: Mode)(implicit request: DataRequest[_]): Call = {
    val maybePurchaseTypeSlug = request.userAnswers.get(PurchaseTypePage).map(PurchaseType.slugOf)
    val maybeParentCode = request.userAnswers.get(PurchaseSubTypePage)
    val maybeChildCode = request.userAnswers.get(PurchaseSubCategoryPage)

    // use centralized mount prefix helper

    (maybePurchaseTypeSlug, maybeParentCode, maybeChildCode) match {
      case (Some(slug), None, Some(child)) =>
        if (child.contains(".")) {
          val parent = child.split("\\.").head
          PurchaseType.fromSlug(slug) match {
            case Some(pt) =>
              val parentKey = pt.toString
              val slugOpt = PurchaseSubCategoryType.slugFor(parentKey, parent).orElse(PurchaseSubCategoryType.firstSlugFor(parentKey))
              slugOpt.map(s => {
                val p = MountPrefix.get
                if (p.isEmpty) Call("GET", s"/$s") else Call("GET", s"$p/$s")
              }).getOrElse(controllers.routes.PurchaseTypeController.onPageLoad(mode))
            case None => controllers.routes.PurchaseTypeController.onPageLoad(mode)
          }
        } else controllers.routes.PurchaseTypeController.onPageLoad(mode)

      case (Some(slug), Some(parent), Some(child)) =>
        val head = parent.split("\\.").headOption.getOrElse(parent)
        val last = parent.split("\\.").lastOption.getOrElse(parent)
        val candidates = Seq(parent, last, head, child).distinct

        val maybeCall = candidates.iterator.map { c =>
          try {
            PurchaseType.fromSlug(slug) match {
              case Some(pt) =>
                val parentKey = pt.toString
                val slugPath = PurchaseSubCategoryType.pathFor(parentKey, c)
                val p = MountPrefix.get
                Some(Call("GET", if (p.isEmpty) s"/$slugPath" else s"$p/$slugPath"))
              case None => None
            }
          } catch { case _: Throwable => None }
        }.collectFirst { case Some(call) => call }

        maybeCall.getOrElse(controllers.routes.PurchaseTypeController.onPageLoad(mode))

      case (Some(slug), Some(_), None) =>
        val p = MountPrefix.get
        if (p.isEmpty) Call("GET", s"/$slug") else Call("GET", s"$p/$slug")

      case _ => controllers.routes.PurchaseTypeController.onPageLoad(mode)
    }
  }
}
