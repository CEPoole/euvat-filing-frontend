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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.test.FakeRequest
import play.api.test.Helpers.GET
import utils.MountPrefix

class MountPrefixSpec extends AnyFreeSpec with Matchers {

  "MountPrefix.get" - {
    "should prefer X-Forwarded-Prefix header and strip trailing slash" in {
      implicit val req = FakeRequest(GET, "/some/path").withHeaders(("X-Forwarded-Prefix", "/prefix/"))
      MountPrefix.get mustEqual "/prefix"
    }

    "should derive prefix from request path when header absent" in {
      implicit val req = FakeRequest(GET, "/file-eu-vat/fuel-type")
      MountPrefix.get mustEqual "/file-eu-vat"
    }

    "should return empty string for root path" in {
      implicit val req = FakeRequest(GET, "/")
      MountPrefix.get mustEqual ""
    }
  }
}
