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

}
