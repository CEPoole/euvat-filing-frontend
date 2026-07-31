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

package connectors

import models.requests.{AddPurchaseRequest, LatestApplicationRequest, SupplierTaxIdentifierCountRequest, , SupplierVrnCountRequest}
import models.responses.{AddPurchaseResponse, LatestApplicationResponse, TraderKnownFactsResponse, SupplierTaxIdentifierCountResponse, SupplierVrnCountResponse}
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class EuVatRefundsConnectorSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockHttp: HttpClientV2 = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]
  val mockConfig: ServicesConfig = mock[ServicesConfig]

  val baseUrl = "http://localhost:9000/euvat-refunds"

  when(mockConfig.baseUrl("euvat-refunds")).thenReturn("http://localhost:9000")

  val connector = new EuVatRefundsConnector(mockConfig, mockHttp)

  "EuVatRefundsConnector.retrieveTradersKnownFacts" should {

    "call the correct URL and return the expected response" in {
      val expected = TraderKnownFactsResponse(
        vatRegNumber = 123,
        traderName   = Some("ABC GmbH"),
        tradeClass   = Some("49200")
      )

      // Mock GET call
      when(mockHttp.get(any())(any())).thenReturn(mockRequestBuilder)

      // Mock execute returning expected response
      when(mockRequestBuilder.execute[TraderKnownFactsResponse](any(), any()))
        .thenReturn(Future.successful(expected))

      val result = connector.retrieveTradersKnownFacts().futureValue

      result shouldBe expected

      verify(mockHttp).get(url"$baseUrl/traders/get-known-facts")
      verify(mockRequestBuilder).execute[TraderKnownFactsResponse](any(), any())
    }

    "propagate failures from the HTTP client" in {
      val failure = new RuntimeException("boom")

      when(mockHttp.get(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[TraderKnownFactsResponse](any(), any()))
        .thenReturn(Future.failed(failure))

      val result = connector.retrieveTradersKnownFacts()

      whenReady(result.failed) { ex =>
        ex shouldBe failure
      }
    }
  }

  "EuVatRefundsConnector.getLatestApplications" should {

    val request = LatestApplicationRequest(
      applicantVatRegNumber = "123456789",
      refundingCountry = Some("LV"),
      startDate = Some(LocalDateTime.of(2025, 2, 1, 0, 0)),
      endDate = Some(LocalDateTime.of(2025, 5, 31, 0, 0)),
      representativeId = Some("rep123"),
      maxNumber = 10,
      orderBy = None,
      sortOrder = None,
      startAt = None
    )
    val expectedResponse = LatestApplicationResponse(
      applications = List.empty,
      totalApplication = 0
    )

    "call the correct URL and return the expected response" in {
      reset(mockHttp, mockRequestBuilder)
      
      when(mockHttp.post(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[LatestApplicationResponse](any(), any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = connector.getLatestApplications(request).futureValue

      result shouldBe expectedResponse

      verify(mockHttp).post(url"$baseUrl/get-latest-application")
      verify(mockRequestBuilder).execute[LatestApplicationResponse](any(), any())
    }

    "propagate failures from the HTTP client" in {
      val failure = new RuntimeException("boom")

      when(mockHttp.post(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[LatestApplicationResponse](any(), any()))
        .thenReturn(Future.failed(failure))

      val result = connector.getLatestApplications(request)

      whenReady(result.failed) { ex =>
        ex shouldBe failure
      }
    }
  }

  "EuVatRefundsConnector.getSupplierVrnCount" should {

    val request = SupplierVrnCountRequest(
      applicationId = 133,
      itemNumber = 4,
      vatNumber = "500000881",
      invoiceNumber = "a444"
    )
    val expectedResponse = SupplierVrnCountResponse(duplicateCount = 1)

    "call the correct URL and return the expected response" in {
      reset(mockHttp, mockRequestBuilder)

      when(mockHttp.post(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[SupplierVrnCountResponse](any(), any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = connector.getSupplierVrnCount(request).futureValue

      result shouldBe expectedResponse

      verify(mockHttp).post(url"$baseUrl/get-supplier-vrn-count")
      verify(mockRequestBuilder).execute[SupplierVrnCountResponse](any(), any())
    }

    "propagate failures from the HTTP client" in {
      val failure = new RuntimeException("boom")

      when(mockHttp.post(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[SupplierVrnCountResponse](any(), any()))
        .thenReturn(Future.failed(failure))

      val result = connector.getSupplierVrnCount(request)

      whenReady(result.failed) { ex =>
        ex shouldBe failure
      }
    }
  }

  "EuVatRefundsConnector.addPurchase" should {

    val request = AddPurchaseRequest(
      applicationId = 123456,
      goodsDescriptionCategory = "1",
      goodsDescriptionText = Some("Fuel"),
      purchaseSubcategory = None,
      simplifiedInvoiceIndicator = None,
      supplierName = None,
      supplierAddress1 = None,
      supplierAddress2 = None,
      supplierAddress3 = None,
      supplierVatRegNumber = None,
      supplierTaxIdentifier = None,
      invoiceDate = None,
      invoiceNumber = None,
      currencyCode = None,
      taxableAmount = None,
      vatAmount = None,
      deductibleVatAmount = None,
      updateSequenceNumber = 1
    )

    val expectedResponse = AddPurchaseResponse(itemNumber = 4, updateSequenceNumber = 1)

    "call the correct URL and return the expected response" in {
      reset(mockHttp, mockRequestBuilder)

      when(mockHttp.post(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[AddPurchaseResponse](any(), any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = connector.addPurchase(request).futureValue

      result shouldBe expectedResponse

      verify(mockHttp).post(url"$baseUrl/add-purchase")
      verify(mockRequestBuilder).execute[AddPurchaseResponse](any(), any())
    }

    "propagate failures from the HTTP client" in {
      when(mockHttp.post(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[AddPurchaseResponse](any(), any()))
        .thenReturn(Future.failed(new RuntimeException("boom")))

      whenReady(connector.addPurchase(request).failed) { ex =>
        ex shouldBe a[RuntimeException]
      }
    }

  }

  "EuVatRefundsConnector.getSupplierTaxIdentifierCount" should {

    val requestPayload = SupplierTaxIdentifierCountRequest(applicationId = 123L, itemNumber = 1, taxIdentifier = "TAX123", invoiceNumber = "INV1")
    val expectedResponse = SupplierTaxIdentifierCountResponse(duplicateCount = 2)

    "call the correct URL and return the expected response" in {
      reset(mockHttp, mockRequestBuilder)

      when(mockHttp.post(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[SupplierTaxIdentifierCountResponse](any(), any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = connector.getSupplierTaxIdentifierCount(requestPayload).futureValue

      result shouldBe expectedResponse

      verify(mockHttp).post(url"$baseUrl/get-supplier-taxIdentifier-count")
      verify(mockRequestBuilder).execute[SupplierTaxIdentifierCountResponse](any(), any())
    }

    "propagate failures from the HTTP client" in {
      when(mockHttp.post(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[SupplierTaxIdentifierCountResponse](any(), any()))
        .thenReturn(Future.failed(new RuntimeException("boom")))

      whenReady(connector.getSupplierTaxIdentifierCount(requestPayload).failed) { ex =>
        ex shouldBe a[RuntimeException]
      }
    }
  }

  "EuVatRefundsConnector.addPurchase" should {

    val request = AddPurchaseRequest(
      applicationId = 123456,
      goodsDescriptionCategory = Some("1234"),
      goodsDescriptionText = Some("Fuel"),
      purchaseSubcategory = None,
      simplifiedInvoiceIndicator = None,
      supplierName = None,
      supplierAddress1 = None,
      supplierAddress2 = None,
      supplierAddress3 = None,
      supplierVatRegNumber = None,
      supplierTaxIdentifier = None,
      invoiceDate = None,
      invoiceNumber = None,
      currencyCode = None,
      taxableAmount = None,
      vatAmount = None,
      deductibleVatAmount = None,
      updateSequenceNumber = None
    )

    val expectedResponse = AddPurchaseResponse(itemNumber = 4, updateSequenceNumber = 1)

    "call the correct URL and return the expected response" in {
      reset(mockHttp, mockRequestBuilder)

      when(mockHttp.post(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[AddPurchaseResponse](any(), any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = connector.addPurchase(request).futureValue

      result shouldBe expectedResponse

      verify(mockHttp).post(url"$baseUrl/add-purchase")
      verify(mockRequestBuilder).execute[AddPurchaseResponse](any(), any())
    }

    "propagate failures from the HTTP client" in {
      when(mockHttp.post(any())(any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[AddPurchaseResponse](any(), any()))
        .thenReturn(Future.failed(new RuntimeException("boom")))

      whenReady(connector.addPurchase(request).failed) { ex =>
        ex shouldBe a[RuntimeException]
      }
    }
  }
}
