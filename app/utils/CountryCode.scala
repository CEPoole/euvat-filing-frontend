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

import models.UserAnswers
import pages.{RefundingCountryNamePage, RefundingCountryPage}

object CountryCode {

  def findCountryCode(userAnswers: UserAnswers): Option[String] = {
    userAnswers
      .get(RefundingCountryPage)
      .orElse(
        userAnswers
          .get(RefundingCountryNamePage)
          .map { stored =>
            val parts = stored.split(",", 2).map(_.trim).filter(_.nonEmpty)
            val isoLike = parts.find(p => p.matches("(?i)^[A-Z]{2}$"))
            isoLike.map(_.toUpperCase).getOrElse(parts.lastOption.getOrElse(stored.trim))
          }
      )
  }

}
