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

package models

import base.SpecBase

class PurchaseSubCategoryTypeSpec extends SpecBase {

  "PurchaseSubCategoryType" - {
    "slugFor should return configured slug when present" in {
      PurchaseSubCategoryType.slugFor("fuel", "1.1") mustBe Some("fuel-type")
    }

    "pathFor should return configured slug when present" in {
      PurchaseSubCategoryType.pathFor("fuel", "1.1") mustBe "fuel-type"
    }

    "pathFor should fall back to parentKey-parentCode when no mapping" in {
      PurchaseSubCategoryType.pathFor("unknownKey", "2.4") mustBe "unknownKey-2-4"
    }
  }
}
