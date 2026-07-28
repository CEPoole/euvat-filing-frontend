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

package models.responses

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class SupplierVrnCountResponseSpec extends AnyFreeSpec with Matchers {

  private val sampleResponse = SupplierVrnCountResponse(duplicateCount = 3)

  "SupplierVrnCountResponse" - {

    "must serialise to JSON correctly" in {
      (Json.toJson(sampleResponse) \ "duplicateCount").as[Int] mustEqual 3
    }

    "must deserialise from JSON correctly" in {
      Json.obj("duplicateCount" -> 3).as[SupplierVrnCountResponse] mustEqual sampleResponse
    }

    "must round-trip through JSON" in {
      Json.toJson(sampleResponse).as[SupplierVrnCountResponse] mustEqual sampleResponse
    }
  }
}