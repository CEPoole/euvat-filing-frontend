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

package models.requests

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class SupplierVrnCountRequestSpec extends AnyFreeSpec with Matchers {

  private val sampleRequest = SupplierVrnCountRequest(
    applicationId = 133,
    itemNumber    = 4,
    vatNumber     = "500000881",
    invoiceNumber = "a444"
  )

  "SupplierVrnCountRequest" - {

    "must serialise to JSON correctly" in {
      val json = Json.toJson(sampleRequest)
      (json \ "applicationId").as[Long] mustEqual 133L
      (json \ "itemNumber").as[Int] mustEqual 4
      (json \ "vatNumber").as[String] mustEqual "500000881"
      (json \ "invoiceNumber").as[String] mustEqual "a444"
    }

    "must deserialise from JSON correctly" in {
      val json = Json.obj(
        "applicationId" -> 133,
        "itemNumber"    -> 4,
        "vatNumber"     -> "500000881",
        "invoiceNumber" -> "a444"
      )
      json.as[SupplierVrnCountRequest] mustEqual sampleRequest
    }

    "must round-trip through JSON" in {
      Json.toJson(sampleRequest).as[SupplierVrnCountRequest] mustEqual sampleRequest
    }
  }
}