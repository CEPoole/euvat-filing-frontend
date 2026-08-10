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

import models.PurchaseType

object PurchaseTypeCode {
  private val codes: Map[PurchaseType, String] = Map(
    PurchaseType.Fuel         -> "1",
    PurchaseType.Transport    -> "3",
    PurchaseType.FoodAndDrink -> "7",
    PurchaseType.Luxuries     -> "9",
    PurchaseType.Other        -> "10"
  )
  def codeFor(pt: PurchaseType): String = codes(pt)
}
