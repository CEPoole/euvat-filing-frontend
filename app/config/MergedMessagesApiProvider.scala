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
import play.api.{Configuration, Environment}
import play.api.i18n.{DefaultMessagesApi, Langs, MessagesApi}
import scala.io.Source

@Singleton
class MergedMessagesApiProvider @Inject() (
  env: Environment,
  config: Configuration,
  langs: Langs
) extends Provider[MessagesApi] {

  private def readEntries(name: String): Seq[(String, String)] = {
    env.resourceAsStream(name) match {
      case Some(is) =>
        val src = Source.fromInputStream(is, "UTF-8")
        try {
          src
            .getLines()
            .toSeq
            .map(_.trim)
            .filter(l => l.nonEmpty && !l.startsWith("#"))
            .flatMap { line =>
              val idx = line.indexOf('=')
              if (idx > 0) Some(line.substring(0, idx).trim -> line.substring(idx + 1).trim)
              else None
            }
        } finally src.close()
      case None => Seq.empty
    }
  }

  private def loadMergedMessages(): Map[String, Map[String, String]] = {
    val langsToLoad = Seq("en", "cy")
    // candidate files to read in order of precedence
    def filesFor(lang: String) = Seq("messages", s"messages.$lang", s"messages.purchase.$lang")

    langsToLoad.map { lang =>
      val entries = filesFor(lang).flatMap(readEntries)
      lang -> entries.toMap
    }.toMap
  }

  override def get(): MessagesApi = {
    val merged =
      try loadMergedMessages()
      catch { case _: Throwable => Map.empty }
    new DefaultMessagesApi(merged, langs)
  }
}
