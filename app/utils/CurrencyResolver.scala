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
import pages.RefundingCurrencyPage

object CurrencyResolver {

  /** Return (currencyName, currencySymbol) for the user's selected or default currency for the refunding country. Falls back to ("", "€") when
    * nothing is available.
    */
  def currencyNameAndPrefix(userAnswers: UserAnswers, config: Map[String, Seq[Currency]]): (String, String) = {
    val default = ("Euro", "€")
    def humanizeName(name: String): String =
      name
        .replaceAll("([a-z])([A-Z])", "$1 $2")
        .split("[ _-]+")
        .filter(_.nonEmpty)
        .map(s => s.head.toUpper.toString + s.tail)
        .mkString(" ")

    utils.CountryCode
      .findCountryCode(userAnswers)
      .flatMap { countryCode =>
        val currencies = config(countryCode)
        val selected = userAnswers
          .get(RefundingCurrencyPage)
          .flatMap(code => currencies.find(_._2 == code))

        selected.orElse(currencies.headOption).map { case Currency(name, _, symbol) => (humanizeName(name), symbol) }
      }
      .getOrElse(default)
  }

}
