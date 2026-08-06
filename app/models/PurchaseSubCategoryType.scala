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

/** Mapping helper for purchase sub-category pages.
  *
  * Provides a canonical URL slug for a given parent key and parent code so controllers and views can produce friendly paths and titles.
  */
object PurchaseSubCategoryType {

  // Map of (parentKey -> (parentCode -> slug))
  private val mapping: Map[String, Map[String, String]] = Map(
    "fuel" -> Map(
      "1.1"  -> "fuel-type",
      "1.2"  -> "fuel-type-or-vehicle",
      "1.3"  -> "fuel-type",
      "1.8"  -> "vehicle-use",
      "1.9"  -> "vehicle-use",
      "1.10" -> "fuel-type",
      "1.11" -> "fuel-type"
    ),
    "transport" -> Map(
      "3.1" -> "what-transport-cost",
      "3.2" -> "what-transport-cost",
      "3.3" -> "what-transport-cost",
      "3.4" -> "what-transport-cost",
      "3.5" -> "vehicle-use",
      "3.6" -> "vehicle-use",
      "3.7" -> "vehicle-use",
      "3.8" -> "vehicle-use"
    ),
    "foodAndDrink" -> Map(
      "7.1" -> "who-food-drink-for",
      "7.2" -> "who-food-drink-for"
    ),
    "luxuries" -> Map(
      "9.3" -> "cost-for-publicity-purposes"
    ),
    "other" -> Map(
      "10.5"  -> "property-purchase-type",
      "10.17" -> "property-cost-type"
    )
  )

  def slugFor(parentKey: String, parentCode: String): Option[String] =
    mapping.get(parentKey).flatMap(_.get(parentCode))

  /** Returns the first available slug for a parent key, if any. Used as a sensible default when a specific parent code does not have an explicit
    * mapping.
    */
  def firstSlugFor(parentKey: String): Option[String] =
    // Choose a deterministic first slug by sorting parent codes numerically/alphabetically
    mapping.get(parentKey).flatMap(m => m.toSeq.sortBy(_._1).headOption.map(_._2))

  /** Builds a friendly path fragment for a parent. Falls back to a generic `parentKey-parentCode` form when no explicit mapping exists.
    */
  def pathFor(parentKey: String, parentCode: String): String =
    // Prefer an exact slug mapping. If none exists for the given parentCode
    // and the parentCode is a top-level code (e.g. "1"), try to find a
    // child's mapping (e.g. "1.1" -> "fuel-type") and reuse that slug so
    // friendly routes like `/fuel-type` still work when the parent code is
    // the shorter form.
    slugFor(parentKey, parentCode)
      .orElse(
        if (!parentCode.contains('.'))
          mapping.get(parentKey).flatMap(m => m.toSeq.filter { case (k, _) => k.startsWith(parentCode + ".") }.sortBy(_._1).headOption.map(_._2))
        else None
      )
      .getOrElse(s"$parentKey-${parentCode.replace('.', '-')}")
}
