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

package page.flow

import base.SpecBase
import models.{NormalMode, UserAnswers, SupplierTaxNumber}
import models.responses.SupplierTaxIdentifierCountResponse
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import pages.{RefundingCountryPage, SupplierTaxNumberPage, InvoiceNumberPage, SupplierTaxIdentifierWarningShownPage, AddPurchaseResponsePage}
import queries.ClaimApplicationResponseQuery
import models.responses.{AddPurchaseResponse, ApplicationResponse}
import play.api.inject.bind
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.SessionRepository

import scala.concurrent.Future

class SupplierTaxIdentifierWarningFlowSpec extends SpecBase with MockitoSugar {

  "End-to-end supplier tax identifier warning flow" - {

    "shows warning when duplicate count > 0 and continues to total on confirmation" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      when(mockEuVatRefundsService.getSupplierTaxIdentifierCount(any())(any()))
        .thenReturn(Future.successful(SupplierTaxIdentifierCountResponse(duplicateCount = 1)))

      val userAnswers: UserAnswers = emptyUserAnswers
        .set(RefundingCountryPage, "DE").success.value
        .set(SupplierTaxNumberPage, SupplierTaxNumber.Taxidentifiernumber).success.value
        .set(InvoiceNumberPage, "INV1").success.value
        .set(ClaimApplicationResponseQuery, ApplicationResponse(applicationId = 123, applicationNumber = "APP123", updateSeqNumber = 1)).success.value
        .set(AddPurchaseResponsePage, AddPurchaseResponse(itemNumber = 1, updateSequenceNumber = 1)).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        // submit supplier tax identifier -> should redirect to warning
        val req = FakeRequest(POST, controllers.routes.SupplierTaxIdentifierNumberController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody("value" -> "ABC123")

        val res = route(application, req).value
        status(res) mustEqual SEE_OTHER
        redirectLocation(res).value mustEqual controllers.routes.SupplierTaxIdentifierWarningController.onPageLoad(NormalMode).url

        // confirm on warning -> should go to total purchase amount
        val cont = FakeRequest(POST, controllers.routes.SupplierTaxIdentifierWarningController.onSubmit(NormalMode).url)
        val res2 = route(application, cont).value
        status(res2) mustEqual SEE_OTHER
        redirectLocation(res2).value mustEqual controllers.routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode).url
      }
    }

    "continues directly when duplicate count == 0" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      when(mockEuVatRefundsService.getSupplierTaxIdentifierCount(any())(any()))
        .thenReturn(Future.successful(SupplierTaxIdentifierCountResponse(duplicateCount = 0)))

      val userAnswers: UserAnswers = emptyUserAnswers
        .set(RefundingCountryPage, "DE").success.value
        .set(SupplierTaxNumberPage, SupplierTaxNumber.Taxidentifiernumber).success.value
        .set(InvoiceNumberPage, "INV1").success.value
        .set(ClaimApplicationResponseQuery, ApplicationResponse(applicationId = 123, applicationNumber = "APP123", updateSeqNumber = 1)).success.value
        .set(AddPurchaseResponsePage, AddPurchaseResponse(itemNumber = 1, updateSequenceNumber = 1)).success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val req = FakeRequest(POST, controllers.routes.SupplierTaxIdentifierNumberController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody("value" -> "ABC123")

        val res = route(application, req).value
        status(res) mustEqual SEE_OTHER
        redirectLocation(res).value mustEqual controllers.routes.TotalPurchaseAmountBeforeVatController.onPageLoad(NormalMode).url
      }
    }

    "when warning flag set, invoice unchanged routes back to warning" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val userAnswers: UserAnswers = emptyUserAnswers
        .set(SupplierTaxIdentifierWarningShownPage, true).success.value
        .set(InvoiceNumberPage, "INV1").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val req = FakeRequest(POST, controllers.routes.InvoiceNumberController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody("value" -> "INV1")

        val res = route(application, req).value
        status(res) mustEqual SEE_OTHER
        redirectLocation(res).value mustEqual controllers.routes.SupplierTaxIdentifierWarningController.onPageLoad(NormalMode).url
      }
    }

    "when warning flag set, invoice changed routes to supplier tax identifier" in {
      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val userAnswers: UserAnswers = emptyUserAnswers
        .set(SupplierTaxIdentifierWarningShownPage, true).success.value
        .set(InvoiceNumberPage, "INV1").success.value

      val application = applicationBuilder(userAnswers = Some(userAnswers))
        .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
        .build()

      running(application) {
        val req = FakeRequest(POST, controllers.routes.InvoiceNumberController.onSubmit(NormalMode).url)
          .withFormUrlEncodedBody("value" -> "INV2")

        val res = route(application, req).value
        status(res) mustEqual SEE_OTHER
        redirectLocation(res).value mustEqual controllers.routes.SupplierTaxIdentifierNumberController.onPageLoad(NormalMode).url
      }
    }
  }
}
