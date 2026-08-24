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

package utils

import base.SpecBase
import play.api.test.FakeRequest
import play.api.data.Form
import play.api.data.Forms.*
import models.requests.DataRequest
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers.any
import repositories.SessionRepository
import play.api.mvc.Results.*
import play.api.test.Helpers.*
import scala.concurrent.Future
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.config.ConfigFactory
import models.{CheckMode, NormalMode, PurchaseType}
import play.api.mvc.Call
import org.mockito.Mockito.{verify, never, times}
import org.mockito.ArgumentMatchers.{any => anyA}
import org.mockito.ArgumentCaptor

class ControllerHelpersSpec extends SpecBase {

  "preparedFormFromAnswers" - {
    "returns empty form when no value stored" in {
      implicit val request: DataRequest[?] = DataRequest(FakeRequest("GET", "/"), userAnswersId, None, None, emptyUserAnswers)

      val form: Form[String] = Form(single("v" -> text))

      val prepared = ControllerHelpers.preparedFormFromAnswers(_.get(pages.SuppliersNamePage), form)

      prepared.value mustBe None
    }

    "returns filled form when value present in UserAnswers" in {
      // store a value into UserAnswers
      val updated = emptyUserAnswers.set(pages.SuppliersNamePage, "Acme Ltd").success.value
      implicit val request: DataRequest[?] = DataRequest(FakeRequest("GET", "/"), userAnswersId, None, None, updated)

      val form: Form[String] = Form(single("v" -> text))

      val prepared = ControllerHelpers.preparedFormFromAnswers(_.get(pages.SuppliersNamePage), form)

      prepared.value.value mustBe "Acme Ltd"
    }
  }

  "persistAndThen" - {
    "persists built UserAnswers once and runs continuation" in {
      // prepare built answers and a Try
      val builtAnswers = emptyUserAnswers.set(pages.SuppliersNamePage, "PersistMe").success.value
      val userAnswersTry = Success(builtAnswers)

      // mock session repository to accept the set
      val mockRepo = mock[SessionRepository]
      when(mockRepo.set(any[models.UserAnswers])) thenReturn Future.successful(true)

      implicit val request: DataRequest[?] = DataRequest(FakeRequest("POST", "/"), userAnswersId, None, None, builtAnswers)

      val fut = ControllerHelpers.persistAndThen(userAnswersTry, mockRepo) { ua =>
        Future.successful(Ok("done"))
      }

      val result = fut.futureValue

      result.header.status mustBe OK
    }
  }

  "currencySymbolFromSession" - {
    "falls back to Euro when no country selected" in {
      implicit val request: DataRequest[?] = DataRequest(FakeRequest("GET", "/"), userAnswersId, None, None, emptyUserAnswers)

      // create a minimal HOCON configuration with a single currency mapping
      // so the `ConfigCurrencyMapping` constructor can read `currency.mapping`.
      val conf = play.api.Configuration(
        ConfigFactory.parseString(
          """
            |currency.mapping {
            |  DEFAULT = ["euro|EUR|€"]
            |}
          """.stripMargin
        )
      )

      val cfg = new ConfigCurrencyMapping(conf)

      ControllerHelpers.currencySymbolFromSession(emptyUserAnswers, cfg) mustBe "€"
    }
  }

  "compareWithPage" - {
    "returns true when comparator matches stored value" in {
      val updated = emptyUserAnswers.set(pages.TotalPurchaseAmountBeforeVatPage, BigDecimal(100)).success.value

      ControllerHelpers.compareWithPage(BigDecimal(120), pages.TotalPurchaseAmountBeforeVatPage, updated)(_ >= _) mustBe true
    }

    "returns false when comparator does not match stored value" in {
      val updated = emptyUserAnswers.set(pages.TotalPurchaseAmountBeforeVatPage, BigDecimal(100)).success.value

      ControllerHelpers.compareWithPage(BigDecimal(80), pages.TotalPurchaseAmountBeforeVatPage, updated)(_ >= _) mustBe false
    }
  }

  "shortCircuitPersistAndThen" - {
    "short-circuits to purchase CYA when in CheckMode and value unchanged" in {
      // prepare UserAnswers with a purchase type and stored value
      val ua = emptyUserAnswers
        .set(pages.PurchaseTypePage, PurchaseType.Fuel).success.value
        .set(pages.TotalVatPaidPage, BigDecimal(10)).success.value

      val mockRepo = mock[SessionRepository]
      when(mockRepo.set(any[models.UserAnswers])) thenReturn Future.successful(true)

      val fut = ControllerHelpers.shortCircuitPersistAndThen[
        BigDecimal
      ](
        pages.TotalVatPaidPage,
        BigDecimal(10),
        CheckMode,
        ua,
        mockRepo,
        Call("GET", "/next"),
        controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
      ) { _ =>
        Future.successful(Ok("saved"))
      }

      val res = fut.futureValue

      res.header.status mustBe SEE_OTHER
      redirectLocation(fut) mustBe Some(controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url)

      // persisted should NOT have been called because we short-circuited
      verify(mockRepo, never()).set(anyA())
    }

    "persists and calls continuation when value changes" in {
      val ua = emptyUserAnswers.set(pages.PurchaseTypePage, PurchaseType.Fuel).success.value

      val mockRepo = mock[SessionRepository]
      when(mockRepo.set(any[models.UserAnswers])) thenReturn Future.successful(true)

      val fut = ControllerHelpers.shortCircuitPersistAndThen[
        BigDecimal
      ](
        pages.TotalVatPaidPage,
        BigDecimal(20),
        CheckMode,
        ua,
        mockRepo,
        Call("GET", "/next"),
        controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
      ) { _ =>
        Future.successful(Ok("saved"))
      }

      val res = fut.futureValue

      res.header.status mustBe OK

      // persisted should have been called once
      verify(mockRepo, times(1)).set(anyA())
    }

    "persists and calls continuation when not in CheckMode" in {
      val ua = emptyUserAnswers

      val mockRepo = mock[SessionRepository]
      when(mockRepo.set(any[models.UserAnswers])) thenReturn Future.successful(true)

      val fut = ControllerHelpers.shortCircuitPersistAndThen[
        BigDecimal
      ](
        pages.TotalVatPaidPage,
        BigDecimal(20),
        NormalMode,
        ua,
        mockRepo,
        Call("GET", "/next"),
        controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad()
      ) { _ =>
        Future.successful(Ok("saved"))
      }

      val res = fut.futureValue

      res.header.status mustBe OK
      verify(mockRepo, times(1)).set(anyA())
    }
  }

  "markArrivalAndRender" - {
    "marks arrival and persists when in CheckMode and flag missing" in {
      val page = pages.PurchaseSubTypeArrivedFromCheckYourAnswersPage

      val ua = emptyUserAnswers

      val mockRepo = mock[SessionRepository]
      when(mockRepo.set(any[models.UserAnswers])) thenReturn Future.successful(true)

      implicit val request: DataRequest[?] = DataRequest(FakeRequest("GET", "/"), userAnswersId, None, None, ua)

      val fut = ControllerHelpers.markArrivalAndRender(page, CheckMode, ua, mockRepo) { updated =>
        Future.successful(Ok("rendered"))
      }

      val res = fut.futureValue
      res.header.status mustBe OK

      val captor = org.mockito.ArgumentCaptor.forClass(classOf[models.UserAnswers])
      verify(mockRepo, times(1)).set(captor.capture())
      val saved = captor.getValue
      saved.get(page).value mustBe true
    }

    "does not persist when flag already set in CheckMode" in {
      val page = pages.PurchaseSubTypeArrivedFromCheckYourAnswersPage

      val ua = emptyUserAnswers.set(page, true).success.value

      val mockRepo = mock[SessionRepository]
      when(mockRepo.set(any[models.UserAnswers])) thenReturn Future.successful(true)

      implicit val request: DataRequest[?] = DataRequest(FakeRequest("GET", "/"), userAnswersId, None, None, ua)

      val fut = ControllerHelpers.markArrivalAndRender(page, CheckMode, ua, mockRepo) { updated =>
        Future.successful(Ok("rendered"))
      }

      val res = fut.futureValue
      res.header.status mustBe OK

      verify(mockRepo, never()).set(anyA())
    }

    "does not persist when in NormalMode" in {
      val page = pages.PurchaseSubTypeArrivedFromCheckYourAnswersPage

      val ua = emptyUserAnswers

      val mockRepo = mock[SessionRepository]
      when(mockRepo.set(any[models.UserAnswers])) thenReturn Future.successful(true)

      implicit val request: DataRequest[?] = DataRequest(FakeRequest("GET", "/"), userAnswersId, None, None, ua)

      val fut = ControllerHelpers.markArrivalAndRender(page, NormalMode, ua, mockRepo) { updated =>
        Future.successful(Ok("rendered"))
      }

      val res = fut.futureValue
      res.header.status mustBe OK

      verify(mockRepo, never()).set(anyA())
    }
  }

}
