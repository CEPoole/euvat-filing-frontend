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

package views

import base.SpecBase
import forms.PurchaseSubTypeFormProvider
import play.api.test.Helpers.*
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import play.api.mvc.Call

class PurchaseSubTypeViewSpec extends SpecBase {

  val formProvider = new PurchaseSubTypeFormProvider()
  val form = formProvider()

  "PurchaseSubTypeView" - {

    "should render a single H1, no H2, and caption inside H1" in {
      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val view = application.injector.instanceOf[views.html.PurchaseSubTypeView]

        val html = view(form, Seq.empty[RadioItem], "page.title", "Heading text", Call("POST", "/submit"), "/back")(play.api.test.FakeRequest(),
                                                                                                                    messages(application)
                                                                                                                   ).toString()

        // Limit assertions to the <main> region to avoid header/footer noise
        val mainStart = html.indexOf("<main")
        val mainEnd = html.indexOf("</main>")
        val mainHtml = if (mainStart >= 0 && mainEnd > mainStart) html.substring(mainStart, mainEnd) else html

        val h1Count = "(?i)<h1\\b".r.findAllMatchIn(mainHtml).length
        val h2Count = "(?i)<h2\\b".r.findAllMatchIn(mainHtml).length

        h1Count mustBe 1
        h2Count mustBe 0

        val spanIndex = mainHtml.indexOf("<span class=\"govuk-caption-l\">")
        val h1Close = mainHtml.indexOf("</h1>")

        spanIndex must be >= 0
        spanIndex must be < h1Close
      }
    }

  }
}
