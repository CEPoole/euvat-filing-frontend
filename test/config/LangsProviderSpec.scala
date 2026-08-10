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
import play.api.Configuration

class LangsProviderSpec extends AnyFreeSpec with Matchers {

  "LangsProvider" - {
    "should produce a DefaultLangs instance for configured languages" in {
      val cfg = Configuration.from(Map("play.i18n.langs" -> Seq("en", "cy")))
      val provider = new LangsProvider(cfg)
      val langs = provider.get()

      // implementation returns DefaultLangs; ensure type is as expected
      langs.getClass.getSimpleName must include("DefaultLangs")
    }
  }
}
