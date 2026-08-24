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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers.{SEE_OTHER, defaultAwaitTimeout, status}
import repositories.SessionRepository
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.Results

class SaveAndRedirectSpec extends SpecBase with MockitoSugar {

  "SaveAndRedirect.saveTryAndRedirect" - {
    "must persist successful Try and redirect" in {
      val mockRepo = mock[SessionRepository]
      when(mockRepo.set(any())).thenReturn(Future.successful(true))

      val t = scala.util.Success(emptyUserAnswers)

      val f = SaveAndRedirect.saveTryAndRedirect(t, mockRepo, controllers.routes.JourneyRecoveryController.onPageLoad())
      status(f) mustEqual SEE_OTHER
    }

    "must return InternalServerError when Try is Failure" in {
      val mockRepo = mock[SessionRepository]
      val t = scala.util.Failure(new RuntimeException("boom"))
      val f = SaveAndRedirect.saveTryAndRedirect(t, mockRepo, controllers.routes.JourneyRecoveryController.onPageLoad())
      status(f) mustEqual play.api.http.Status.INTERNAL_SERVER_ERROR
    }
  }

}
