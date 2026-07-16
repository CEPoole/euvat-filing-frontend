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

import javax.inject.{Inject, Provider, Singleton}
import play.api.Configuration
import play.api.i18n.{Lang, Langs, DefaultLangs}

@Singleton
class LangsProvider @Inject()(config: Configuration) extends Provider[Langs] {
  override def get(): Langs = {
    val langsCfg = config.get[Seq[String]]("play.i18n.langs")
    new DefaultLangs(langsCfg.map(Lang.apply))
  }
}
