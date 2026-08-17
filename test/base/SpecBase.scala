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

package base

import controllers.actions.{FakeIdentifierAction, *}
import models.UserAnswers
import models.responses.{LatestApplicationResponse, TraderKnownFactsResponse}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{BodyParser, BodyParsers}
import play.api.test.FakeRequest
import repositories.SessionRepository
import services.EuVatRefundsService

import scala.concurrent.Future

trait SpecBase
    extends AnyFreeSpec
    with Matchers
    with TryValues
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach {

  val userAnswersId: String = "id"

  def emptyUserAnswers: UserAnswers = UserAnswers(userAnswersId)

  // Default mock for controllers that call the EuVatRefundsService
  protected val mockSessionRepository: SessionRepository = mock[SessionRepository]
  protected val mockEuVatRefundsService: EuVatRefundsService = mock[EuVatRefundsService]

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    org.mockito.Mockito.reset(mockEuVatRefundsService)
    org.mockito.Mockito.reset(mockSessionRepository)
    // safe defaults: no duplicate applications, and simple trader known facts
    when(mockEuVatRefundsService.retrieveTraderKnownFacts()(any()))
      .thenReturn(Future.successful(TraderKnownFactsResponse(vatRegNumber = 999900106, traderName = None, tradeClass = None)))
    when(mockEuVatRefundsService.getLatestApplications(any())(any()))
      .thenReturn(Future.successful(LatestApplicationResponse(applications = List.empty, totalApplication = 0)))
    when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

  }

  def messages(app: Application): Messages = app.injector.instanceOf[MessagesApi].preferred(FakeRequest())

  protected def applicationBuilder(userAnswers: Option[UserAnswers] = None, whichAction: Boolean = false): GuiceApplicationBuilder =
    if (whichAction) {
      new GuiceApplicationBuilder()
        .overrides(
          bind[DataRequiredAction].to[DataRequiredActionImpl],
          bind[IdentifierAction].to[CustomIdentifierAction],
          bind[DataRetrievalAction].toInstance(new CustomFakeDataRetrievalAction(userAnswers)),
          bind[EuVatRefundsService].toInstance(mockEuVatRefundsService)
        )
    } else {
      new GuiceApplicationBuilder()
        .overrides(
          bind[DataRequiredAction].to[DataRequiredActionImpl],
          bind[IdentifierAction].to[FakeIdentifierAction],
          bind[DataRetrievalAction].toInstance(new FakeDataRetrievalAction(userAnswers)),
          bind[EuVatRefundsService].toInstance(mockEuVatRefundsService)
        )
    }

  // Normalize dynamic values in rendered HTML to make string comparisons deterministic in tests.
  // - strips any `nonce="..."` attributes
  // - removes any CSRF hidden input elements entirely
  def normalizeHtml(html: String): String =
    html
      .replaceAll("nonce=\"[^\"]*\"", "nonce=\"\"")
      .replaceAll("(?s)<input[^>]*name=\"csrfToken\"[^>]*>", "")
      .replaceAll("\\s+", " ")
      .trim
}
