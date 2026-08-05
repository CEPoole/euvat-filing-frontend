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

import base.SpecBase
import models.{NormalMode, PurchaseType}
import models.requests.DataRequest
import pages.{PurchaseTypePage, PurchaseSubTypePage, PurchaseSubCategoryPage}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.mvc.Call
import controllers.helpers.PurchaseBackLinkHelper

class PurchaseBackLinkHelperSpec extends SpecBase {

  "PurchaseBackLinkHelper.computeBackTarget" - {

    "should return PurchaseTypeController when no user answers available" in {
      val userAnswers: models.UserAnswers = emptyUserAnswers
      implicit val request = DataRequest(FakeRequest(GET, "/"), userAnswersId, userAnswers)

      val result: Call = PurchaseBackLinkHelper.computeBackTarget(NormalMode)

      result mustEqual controllers.routes.PurchaseTypeController.onPageLoad(NormalMode)
    }

    "should return mounted slug when PurchaseType and PurchaseSubType present and no subcategory" in {
      val userAnswers: models.UserAnswers = emptyUserAnswers
        .set(PurchaseTypePage, PurchaseType.Fuel).success.value
        .set(PurchaseSubTypePage, "1").success.value

      implicit val request = DataRequest(FakeRequest(GET, "/file-eu-vat/foo"), userAnswersId, userAnswers)

      val result: Call = PurchaseBackLinkHelper.computeBackTarget(NormalMode)

      result.method mustEqual "GET"
      result.url mustEqual "/file-eu-vat/fuel-use"
    }

    "should fallback to PurchaseType when child present but parent missing (child without dot)" in {
      val userAnswers: models.UserAnswers = emptyUserAnswers
        .set(PurchaseTypePage, PurchaseType.Fuel).success.value
        .set(PurchaseSubCategoryPage, "1").success.value

      implicit val request = DataRequest(FakeRequest(GET, "/file-eu-vat/foo"), userAnswersId, userAnswers)

      val result: Call = PurchaseBackLinkHelper.computeBackTarget(NormalMode)

      result mustEqual controllers.routes.PurchaseTypeController.onPageLoad(NormalMode)
    }

    "should map to first available slug when child contains dot and parent missing" in {
      val userAnswers: models.UserAnswers = emptyUserAnswers
        .set(PurchaseTypePage, PurchaseType.Fuel).success.value
        .set(PurchaseSubCategoryPage, "1.1").success.value

      implicit val request = DataRequest(FakeRequest(GET, "/file-eu-vat/foo"), userAnswersId, userAnswers)

      val result: Call = PurchaseBackLinkHelper.computeBackTarget(NormalMode)

      result.method mustEqual "GET"
      result.url must startWith("/file-eu-vat/fuel-type")
    }

  }
}
