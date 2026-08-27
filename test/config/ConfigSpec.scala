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

import base.SpecBase
import play.api.Configuration
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest

class ConfigSpec extends SpecBase {

  "Service" - {
    "baseUrl and toString should return protocol://host:port" in {
      val svc = Service("example.com", "8080", "https")
      svc.baseUrl mustEqual "https://example.com:8080"
      svc.toString mustEqual svc.baseUrl
      // implicit conversion
      val asString: String = svc
      asString mustEqual svc.baseUrl
    }

    "ConfigLoader should build a Service from configuration" in {
      val config = Configuration(
        "microservice.services.feedback-frontend.host"     -> "fb.local",
        "microservice.services.feedback-frontend.port"     -> "9514",
        "microservice.services.feedback-frontend.protocol" -> "http"
      )

      val svc = config.get[Service]("microservice.services.feedback-frontend")
      svc.host mustEqual "fb.local"
      svc.port mustEqual "9514"
      svc.protocol mustEqual "http"
    }
  }

  "FrontendAppConfig" - {
    "languageMap contains English and Welsh, and EU countries populated from config" in {
      val base = Map(
        "host"                                             -> "http://localhost:18501",
        "appName"                                          -> "euvat",
        "contact-frontend.host"                            -> "http://localhost:9250",
        "contact-frontend.serviceId"                       -> "euvat-filing-frontend",
        "microservice.services.feedback-frontend.host"     -> "fb.local",
        "microservice.services.feedback-frontend.port"     -> "9514",
        "microservice.services.feedback-frontend.protocol" -> "http",
        "urls.login"                                       -> "http://login",
        "urls.loginContinue"                               -> "http://continue",
        "urls.signOut"                                     -> "http://signout",
        "urls.claimDashboardUrl"                           -> "http://dashboard",
        "features.welsh-translation"                       -> true,
        "timeout-dialog.timeout"                           -> 900,
        "timeout-dialog.countdown"                         -> 120,
        "mongodb.timeToLiveInSeconds"                      -> 900,
        "eu.member-states"                                 -> Map("UK" -> "United Kingdom", "DE" -> "Germany"),
        "allowlist.refund.amend.vrns"                      -> Seq.empty,
        "allowlist.refund.create.vrns"                     -> Seq.empty,
        "refund.start.earliest.month"                      -> 1,
        "refund.start.earliest.year"                       -> 1,
        "refund.start.latest.month"                        -> 1,
        "refund.start.latest.year"                         -> 1
      )

      val config = Configuration(base.toSeq*)
      val appConfig = new FrontendAppConfig(config)

      val langs = appConfig.languageMap
      langs.keySet must contain("en")
      langs.keySet must contain("cy")

      val countries = appConfig.countriesInEU
      countries mustBe Map("UK" -> "United Kingdom", "DE" -> "Germany")
    }
  }
}
