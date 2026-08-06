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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import java.time.LocalDateTime

class AddPurchaseRequestSpec extends AnyWordSpec with Matchers {

  private val request = AddPurchaseRequest(
    applicationId              = 123456,
    goodsDescriptionCategory   = "1",
    goodsDescriptionText       = Some("Fuel"),
    purchaseSubcategory        = Some("5678"),
    simplifiedInvoiceIndicator = Some("N"),
    supplierName               = Some("ABC GmbH"),
    supplierAddress1           = Some("Line 1"),
    supplierAddress2           = Some("Line 2"),
    supplierAddress3           = Some("Line 3"),
    supplierVatRegNumber       = Some("500000881"),
    supplierTaxIdentifier      = Some("TAX123"),
    invoiceDate                = Some(LocalDateTime.of(2025, 2, 1, 0, 0)),
    invoiceNumber              = Some("a444"),
    currencyCode               = Some("EUR"),
    taxableAmount              = Some(BigDecimal(100.00)),
    vatAmount                  = Some(BigDecimal(20.00)),
    deductibleVatAmount        = Some(BigDecimal(20.00)),
    updateSequenceNumber       = 1
  )

  "AddPurchaseRequest JSON format" should {

    "serialize and deserialize correctly" in {
      Json.toJson(request).as[AddPurchaseRequest] shouldBe request
    }

    "deserialize when optional fields are absent" in {
      val json = Json.obj(
        "applicationId"            -> 123456,
        "goodsDescriptionCategory" -> "1",
        "updateSequenceNumber"     -> 1
      )
      val result = json.as[AddPurchaseRequest]

      result.applicationId        shouldBe 123456L
      result.supplierName         shouldBe None
      result.updateSequenceNumber shouldBe 1
    }
  }
}
