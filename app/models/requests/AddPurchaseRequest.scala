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

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDateTime

case class AddPurchaseRequest(
  applicationId: Long,
  goodsDescriptionCategory: String,
  goodsDescriptionText: Option[String] = None,
  purchaseSubcategory: Option[String] = None,
  simplifiedInvoiceIndicator: Option[String] = None,
  supplierName: Option[String] = None,
  supplierAddress1: Option[String] = None,
  supplierAddress2: Option[String] = None,
  supplierAddress3: Option[String] = None,
  supplierVatRegNumber: Option[String] = None,
  supplierTaxIdentifier: Option[String] = None,
  invoiceDate: Option[LocalDateTime] = None,
  invoiceNumber: Option[String] = None,
  currencyCode: Option[String] = None,
  taxableAmount: Option[BigDecimal] = None,
  vatAmount: Option[BigDecimal] = None,
  deductibleVatAmount: Option[BigDecimal] = None,
  updateSequenceNumber: Int
)
object AddPurchaseRequest {
  implicit val format: OFormat[AddPurchaseRequest] = Json.format[AddPurchaseRequest]
}
