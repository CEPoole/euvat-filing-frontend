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

package navigation

import play.api.mvc.Call
import pages.*
import models.{Mode, UserAnswers}
import utils.{ConfigCurrencyMapping, ConfigLanguageMapping, ConfigPurchaseMapping}
import play.api.Configuration
import com.typesafe.config.ConfigFactory

class FakeNavigator(desiredRoute: Call)
    extends Navigator(
      new ConfigCurrencyMapping(
        Configuration(
          ConfigFactory.parseString("""
      currency.mapping {
        BG = ["euro|EUR|€", "bulgarianLev|BGN|лв"]
        EE = ["euro|EUR|€", "estonianKroon|EEK|kr"]
        AT = ["euro|EUR|€"]
      }
    """)
        )
      ),
      new ConfigLanguageMapping(
        Configuration(
          ConfigFactory.parseString("""
        language.mapping {
          AT = ["german", "english"]
          BE = ["english", "german", "french", "dutch"]
          CZ = ["czech"]
        }
      """)
        )
      )
      ,
      new ConfigPurchaseMapping(
        Configuration(
          ConfigFactory.parseString("""
        purchase.mapping {
          DE = ["parent|sub1|purchase.sub.parent.sub1"]
        }
      """)
        )
      )
    ) {

  override def nextPage(page: Page, mode: Mode, userAnswers: UserAnswers): Call =
    desiredRoute
}
