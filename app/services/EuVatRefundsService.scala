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

package services

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.EuVatRefundsConnector
import models.requests.{AddPurchaseRequest, ApplicationRequest, LatestApplicationRequest, SupplierTaxIdentifierCountRequest, SupplierVrnCountRequest}
import models.responses.{AddPurchaseResponse, ApplicationResponse, LatestApplicationResponse, SupplierTaxIdentifierCountResponse, SupplierVrnCountResponse, TraderKnownFactsResponse}
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EuVatRefundsService @Inject() (euVatRefundsConnector: EuVatRefundsConnector, config: FrontendAppConfig)(implicit ec: ExecutionContext)
    extends Logging {
  def retrieveTraderKnownFacts()(implicit hc: HeaderCarrier): Future[TraderKnownFactsResponse] = {
    euVatRefundsConnector.retrieveTradersKnownFacts()
  }

  def getLatestApplications(latestApplicationRequest: LatestApplicationRequest)(implicit hc: HeaderCarrier): Future[LatestApplicationResponse] = {
    euVatRefundsConnector.getLatestApplications(latestApplicationRequest)
  }

  def createApplication(appRequest: ApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationResponse] = {
    euVatRefundsConnector.createApplication(appRequest)
  }

  def addPurchase(request: AddPurchaseRequest)(implicit hc: HeaderCarrier): Future[AddPurchaseResponse] =
    euVatRefundsConnector.addPurchase(request)

  def getSupplierTaxIdentifierCount(request: SupplierTaxIdentifierCountRequest)(implicit
    hc: HeaderCarrier
  ): Future[SupplierTaxIdentifierCountResponse] =
    euVatRefundsConnector.getSupplierTaxIdentifierCount(request)

  def getSupplierVrnCount(request: SupplierVrnCountRequest)(implicit hc: HeaderCarrier): Future[SupplierVrnCountResponse] = {
    euVatRefundsConnector.getSupplierVrnCount(request)
  }

}
