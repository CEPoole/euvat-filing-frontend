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

package forms

import org.scalatest.matchers.must.Matchers
import play.api.data.FormError
import play.api.i18n.Messages
import play.api.test.Helpers.stubMessages

import java.time.YearMonth

class RefundPeriodFormProviderSpec extends FormSpec with Matchers {

  private implicit val messages: Messages = stubMessages()

  val formProvider: RefundPeriodFormProvider = new RefundPeriodFormProvider() {
    override protected def today: java.time.LocalDate = java.time.LocalDate.of(2021, 6, 1)
  }

  "RefundPeriodFormProvider" - {

    "must error when both dates are before earliest" in {
      val form = formProvider(Some(YearMonth.of(2021, 1)), None)

      val data = Map(
        "start.month" -> "12",
        "start.year"  -> "2020",
        "end.month"   -> "12",
        "end.year"    -> "2020"
      )

      val errors = form.bind(data).fold(
        formWithErrors => formWithErrors.errors,
        _ => fail("Expected validation errors")
      )
      errors must contain(FormError("", "refundPeriod.error.beforeEarliest.both", Seq("January 2021")))
    }

    "must error when start only is before earliest" in {
      val form = formProvider(Some(YearMonth.of(2021, 1)), None)

      val data = Map(
        "start.month" -> "12",
        "start.year"  -> "2020",
        "end.month"   -> "02",
        "end.year"    -> "2021"
      )

      val errors = form.bind(data).fold(
        formWithErrors => formWithErrors.errors,
        _ => fail("Expected validation errors")
      )
      errors must contain(FormError("", "refundPeriod.error.beforeEarliest.start", Seq("January 2021")))
    }

    "must prioritise field errors over earliest business rule" in {
      val form = formProvider(Some(YearMonth.of(2021, 1)), None)

      val data = Map(
        "start.month" -> "", // missing month -> field-level error
        "start.year"  -> "2020",
        "end.month"   -> "02",
        "end.year"    -> "2021"
      )

      val errors = form.bind(data).fold(
        formWithErrors => formWithErrors.errors,
        _ => fail("Expected validation errors")
      )

      errors.map(_.message).exists(_.startsWith("refundPeriod.start.error.required")) mustBe true
      errors.map(_.message) must not contain ("refundPeriod.error.beforeEarliest.start")
    }

    "must error when end only is before earliest" in {
      val form = formProvider(Some(YearMonth.of(2021, 1)), None)

      val data = Map(
        "start.month" -> "01",
        "start.year"  -> "2021",
        "end.month"   -> "12",
        "end.year"    -> "2020"
      )

      val errors = form.bind(data).fold(
        formWithErrors => formWithErrors.errors,
        _ => fail("Expected validation errors")
      )
      errors must contain(FormError("", "refundPeriod.error.beforeEarliest.end", Seq("January 2021")))
    }

    "must bind when dates are on or after earliest" in {
      val cutoff = java.time.YearMonth.of(2021, 1)

      val form = formProvider(Some(cutoff), None)

      val data = Map(
        "start.month" -> cutoff.getMonthValue.toString,
        "start.year"  -> cutoff.getYear.toString,
        "end.month"   -> cutoff.getMonthValue.toString,
        "end.year"    -> cutoff.getYear.toString
      )

      val startYm = cutoff
      val endYm = cutoff.plusMonths(2)

      val data2 = Map(
        "start.month" -> startYm.getMonthValue.toString,
        "start.year"  -> startYm.getYear.toString,
        "end.month"   -> endYm.getMonthValue.toString,
        "end.year"    -> endYm.getYear.toString
      )

      form.bind(data2).fold(_ => fail("Expected successful bind"), _ => succeed)
    }

    "must error when period less than 3 months for exempt VRN window (Oct-Nov 2020)" in {
      val form = formProvider(Some(YearMonth.of(2020, 1)), Some(YearMonth.of(2020, 12)))

      val data = Map(
        "start.month" -> "10",
        "start.year"  -> "2020",
        "end.month"   -> "11",
        "end.year"    -> "2020"
      )

      val errors = form.bind(data).fold(
        formWithErrors => formWithErrors.errors,
        _ => fail("Expected validation errors")
      )

      errors.map(_.message) must contain("refundPeriod.error.periodNotLessThan3Months")
    }

    "must prioritise form rules (period length) over earliest business rule" in {
      val form = formProvider(Some(YearMonth.of(2021, 1)), None)

      val data = Map(
        "start.month" -> "10",
        "start.year"  -> "2020",
        "end.month"   -> "11",
        "end.year"    -> "2020"
      )

      val bound = form.bind(data)
      val (mappedForm, _) = formProvider.withMappedErrors(bound)
      val errors = mappedForm.errors

      // period length error should appear and earliest error should not
      errors.map(_.message) must contain("refundPeriod.error.periodNotLessThan3Months")
      errors.map(_.message) must not contain ("refundPeriod.error.beforeEarliest.both")
    }

    "must still prioritise period-length over earliest when earliest is later" in {
      // earliest set to Jan 2025 (later than the submitted dates)
      val form = formProvider(Some(YearMonth.of(2025, 1)), None)

      val data = Map(
        "start.month" -> "10",
        "start.year"  -> "2020",
        "end.month"   -> "11",
        "end.year"    -> "2020"
      )

      val bound = form.bind(data)
      val (mappedForm, _) = formProvider.withMappedErrors(bound)
      val errors = mappedForm.errors

      errors.map(_.message) must contain("refundPeriod.error.periodNotLessThan3Months")
      errors.map(_.message) must not contain ("refundPeriod.error.beforeEarliest.both")
    }

    "must error when either date is after latest" in {
      val form = formProvider(None, Some(YearMonth.of(2020, 12)))

      val data = Map(
        "start.month" -> "01",
        "start.year"  -> "2021",
        "end.month"   -> "01",
        "end.year"    -> "2021"
      )

      val errors = form.bind(data).fold(
        formWithErrors => formWithErrors.errors,
        _ => fail("Expected validation errors")
      )
      errors must contain(FormError("", "refundPeriod.error.afterLatest.both", Seq("December 2020")))
    }

    "must error when start only is after latest" in {
      val form = formProvider(None, Some(YearMonth.of(2020, 12)))

      val data = Map(
        "start.month" -> "01",
        "start.year"  -> "2021",
        "end.month"   -> "12",
        "end.year"    -> "2020"
      )

      val errors = form.bind(data).fold(
        formWithErrors => formWithErrors.errors,
        _ => fail("Expected validation errors")
      )
      errors must contain(FormError("", "refundPeriod.error.afterLatest.start", Seq("December 2020")))
    }

    "must error when end only is after latest" in {
      val form = formProvider(None, Some(YearMonth.of(2020, 12)))

      val data = Map(
        "start.month" -> "12",
        "start.year"  -> "2020",
        "end.month"   -> "01",
        "end.year"    -> "2021"
      )

      val errors = form.bind(data).fold(
        formWithErrors => formWithErrors.errors,
        _ => fail("Expected validation errors")
      )
      errors must contain(FormError("", "refundPeriod.error.afterLatest.end", Seq("December 2020")))
    }
  }
}
