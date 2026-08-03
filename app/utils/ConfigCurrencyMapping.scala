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

import play.api.Configuration

import javax.inject.Inject

class ConfigCurrencyMapping @Inject() (config: Configuration) {

  private val mapping: Map[String, Seq[(String, String, String)]] = {
    val cfg = config.get[Configuration]("currency.mapping")
    cfg.entrySet.map { case (key, _) =>
      val seq = cfg.get[Seq[String]](key).map { entry =>
        val parts = entry.split("\\|", 3)
        (parts(0), parts(1), parts(2))
      }
      key -> seq
    }.toMap
  }

  def currenciesFor(code: String): Seq[(String, String, String)] =
    mapping.getOrElse(code, Seq(("euro", "EUR", "€")))

  def hasMapping(code: String): Boolean = mapping.contains(code)

  def requiresCurrencySelection(code: String): Boolean =
    currenciesFor(code).size > 1

}
