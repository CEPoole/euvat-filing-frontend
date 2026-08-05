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

package config

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Environment
import play.api.Configuration
import play.api.i18n.{Lang, DefaultLangs, DefaultMessagesApi}

class MergedMessagesApiProviderSpec extends AnyFreeSpec with Matchers {

  "MergedMessagesApiProvider" - {
    "should load merged messages for configured langs" in {
      val env = Environment.simple()
      val config = Configuration.empty
      val langs = new DefaultLangs(Seq(Lang("en"), Lang("cy")))

      val provider = new MergedMessagesApiProvider(env, config, langs)
      val api = provider.get()

      val defaultApi = api.asInstanceOf[DefaultMessagesApi]
      // ensure some well-known keys are present in the merged map
      defaultApi.messages.get("en").flatMap(_.get("service.name")) mustBe Some("EU VAT")
      defaultApi.messages.get("en").flatMap(_.get("purchase.sub.fuel.heading")).isDefined mustBe true
    }
  }
}
