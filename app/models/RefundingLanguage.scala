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

package models

import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem

sealed abstract class RefundingLanguage(
  val value: String,
  val code: String
) extends WithName(value)

object RefundingLanguage extends Enumerable.Implicits {

  case object English    extends RefundingLanguage("english", "en")
  case object German     extends RefundingLanguage("german", "de")
  case object French     extends RefundingLanguage("french", "fr")
  case object Dutch      extends RefundingLanguage("dutch", "nl")
  case object Bulgarian  extends RefundingLanguage("bulgarian", "bg")
  case object Spanish    extends RefundingLanguage("spanish", "es")
  case object Turkish    extends RefundingLanguage("turkish", "tr")
  case object Czech      extends RefundingLanguage("czech", "cz")
  case object Danish     extends RefundingLanguage("danish", "da")
  case object Estonian   extends RefundingLanguage("estonian", "ee")
  case object Finnish    extends RefundingLanguage("finnish", "fn")
  case object Swedish    extends RefundingLanguage("swedish", "sw")
  case object Italian    extends RefundingLanguage("italian", "it")
  case object Latvian    extends RefundingLanguage("latvian", "lt")
  case object Lithuanian extends RefundingLanguage("lithuanian", "ln")
  case object Maltese    extends RefundingLanguage("maltese", "ma")
  case object Polish     extends RefundingLanguage("polish", "pl")
  case object Portuguese extends RefundingLanguage("portuguese", "pr")
  case object Romanian   extends RefundingLanguage("romanian", "rm")
  case object Hungarian  extends RefundingLanguage("hungarian", "hg")
  case object Greek      extends RefundingLanguage("greek", "gr")
  case object Slovak     extends RefundingLanguage("slovak", "sk")
  case object Slovenian  extends RefundingLanguage("slovenian", "sl")
  case object Irish      extends RefundingLanguage("irish", "ir")

  val values: Seq[RefundingLanguage] = Seq(
    English,
    German,
    French,
    Dutch,
    Bulgarian,
    Spanish,
    Turkish,
    Czech,
    Danish,
    Estonian,
    Finnish,
    Swedish,
    Italian,
    Latvian,
    Lithuanian,
    Maltese,
    Polish,
    Portuguese,
    Romanian,
    Hungarian,
    Greek,
    Slovak,
    Slovenian,
    Irish
  )

  def options(implicit messages: Messages): Seq[RadioItem] = values.zipWithIndex.map { case (value, index) =>
    RadioItem(
      content = Text(messages(s"refundingLanguage.${value.toString}")),
      value   = Some(value.toString),
      id      = Some(s"value_$index")
    )
  }

  implicit val enumerable: Enumerable[RefundingLanguage] =
    Enumerable(values.map(v => v.toString -> v)*)
}
