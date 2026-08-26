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

import play.api.mvc.RequestHeader

object MountPrefix {

  /** Compute the mount prefix for the current request. Prefers `X-Forwarded-Prefix` header and falls back to deriving the prefix from the request
    * path by removing the last segment.
    */
  def getFromRequest(implicit request: RequestHeader): String =
    request.headers.get("X-Forwarded-Prefix").map(_.stripSuffix("/")).getOrElse {
      request.path.lastIndexOf('/') match {
        case i if i > 0 => request.path.substring(0, i)
        case _          => ""
      }
    }
}
