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

import java.time.LocalDateTime

class LatestApplicationRequestSpec extends AnyFreeSpec with Matchers {

  private val sampleRequest = LatestApplicationRequest(
    applicantVatRegNumber = "123456789",
    refundingCountry      = Some("LV"),
    startDate             = Some(LocalDateTime.of(2025, 2, 1, 0, 0)),
    endDate               = Some(LocalDateTime.of(2025, 5, 31, 0, 0)),
    representativeId      = Some("rep123"),
    maxNumber             = 10,
    orderBy               = Some(0),
    sortOrder             = Some("ASC"),
    startAt               = Some(0)
  )

  "LatestApplicationRequest" - {

    "must serialise with only mandatory fields" in {
      val json = Json.toJson(sampleRequest)
      (json \ "applicantVatRegNumber").as[String] mustEqual "123456789"
      (json \ "refundingCountry").as[String] mustEqual "LV"
      (json \ "maxNumber").as[Int] mustEqual 10
    }

    "must serialise with optional fields populated" in {
      val requestWithOptionals = sampleRequest.copy(
        orderBy   = Some(1),
        sortOrder = Some("ASC"),
        startAt   = Some(0)
      )
      val json = Json.toJson(requestWithOptionals)
      (json \ "orderBy").as[Int] mustEqual 1
      (json \ "sortOrder").as[String] mustEqual "ASC"
      (json \ "startAt").as[Int] mustEqual 0
    }

    "must deserialise with missing optional fields" in {
      val json = Json.obj(
        "applicantVatRegNumber" -> "123456789",
        "maxNumber"             -> 10
      )
      val model = json.as[LatestApplicationRequest]
      model.refundingCountry mustEqual None
      model.startDate mustEqual None
      model.endDate mustEqual None
      model.representativeId mustEqual None
      model.orderBy mustEqual Some(0)
      model.sortOrder mustEqual Some("ASC")
      model.startAt mustEqual Some(0)
    }

    "must round-trip through JSON" in {
      Json.toJson(sampleRequest).as[LatestApplicationRequest] mustEqual sampleRequest
    }
  }
}
