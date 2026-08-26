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
import com.typesafe.config.ConfigFactory
import play.api.Configuration
import utils.CurrencyConfig
import pages.RefundingCurrencyPage

class CurrencyResolverSpec extends SpecBase {

  "CurrencyResolver.currencyNameAndPrefix" - {
    "must return selected currency name and symbol when present" in {
      val confString =
        """
          |currency.mapping = {
          |  BG = ["euro|EUR|€", "bulgarianLev|BGN|лв"]
          |}
        """.stripMargin

      val cfg = Configuration(ConfigFactory.parseString(confString))
      val mapping = new CurrencyConfig(cfg)

      val ua = emptyUserAnswers
        .set(pages.RefundingCountryPage, "BG")
        .success
        .value
        .set(RefundingCurrencyPage, "BGN")
        .success
        .value
      val (name, symbol) = CurrencyResolver.currencyNameAndPrefix(ua, mapping.currencyConfig)
      name.toLowerCase must include("bulgarian")
      symbol mustBe "лв"
    }

    "must fall back to default when nothing present" in {
      val cfg = Configuration(ConfigFactory.parseString("currency.mapping = { }"))
      val mapping = new CurrencyConfig(cfg)
      val (name, symbol) = CurrencyResolver.currencyNameAndPrefix(emptyUserAnswers, mapping.currencyConfig)
      name mustBe "Euro"
      symbol mustBe "€"
    }
  }

}
