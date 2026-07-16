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

import javax.inject.Inject
import play.api.Configuration
import play.api.Environment
import scala.io.Source
import scala.jdk.CollectionConverters.*
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem

// Import commonly used types from the Typesafe config library so the code
// below can use short names instead of fully-qualified class names.
import com.typesafe.config.{ConfigObject, ConfigValueType}

case class PurchaseNode(parent: String, code: String, label: String, children: Seq[PurchaseNode] = Seq.empty)

/**
 * `ConfigPurchaseMapping` loads a declarative purchase mapping from
 * `application.conf` (under `purchase.mapping`) and exposes helpers used by
 * controllers and views to build radio items and lookup subcodes/subcategories.
 *
 * The mapping supports mixed arrays (plain strings and nested objects) and
 * contains logic to normalise label keys that include numeric ordering
 * segments. The class is intentionally defensive: most parsing errors are
 * swallowed and an empty mapping is returned so the application can fall
 * back to sensible defaults.
 */

class ConfigPurchaseMapping @Inject() (config: Configuration = Configuration.empty, env: Environment = Environment.simple()) {

  /**
   * Normalise a label key by dropping a numeric segment that duplicates the
   * first numeric segment of the `code` when present. This mirrors a set of
   * legacy label shapes used in configuration and keeps resulting message
   * keys stable for lookups in message bundles.
   */
  private def normalizeLabel(label: String, code: String): String = {
    if (label == null) return ""
    val prefix = "purchase.sub."
    if (!label.startsWith(prefix)) return label
    if (code == null || code.isEmpty) return label
    val rest = label.substring(prefix.length)
    val parts = rest.split("\\.")
    val codeFirst = code.split("\\.").headOption.getOrElse("")
    // Only drop the numeric segment after the type if it matches the first numeric segment of the entry code
    if (parts.length >= 2 && parts(1) == codeFirst) {
      val newRest = parts.head +: parts.drop(2)
      prefix + newRest.mkString(".")
    } else label
  }


  private val mapping: Map[String, Seq[PurchaseNode]] = try {
    // The raw HOCON node under `purchase.mapping` is the source of truth.
    // It's expected to have top-level keys for country codes (eg "DE", "AT").
    val rootConfig = config.underlying.getConfig("purchase.mapping")

    // Parse a single mapping entry. Entries come in two flavours:
    // - String rows encoded as "parent|code|label"
    // - Nested objects represented as a Play `Configuration` for richer entries
    def parseEntry(entry: Any): PurchaseNode = entry match {
      // Simple string entry: parts are delimited by '|'
      case s: String =>
        val parts = s.split("\\|", 3)
        // Expect exactly three parts; if malformed the downstream logic
        // will still treat missing parts as empty strings.
        PurchaseNode(parts(0), parts(1), parts(2), Seq.empty)

      // Complex object entry: may contain `subcodes` which is itself an
      // array of strings and/or objects. We recursively parse children.
      case c: Configuration =>
        val parent = c.getOptional[String]("parent").getOrElse("")
        val code = c.getOptional[String]("code").getOrElse("")
        val label = c.getOptional[String]("label").getOrElse("")

        // Attempt to read nested `subcodes` list; be defensive as the
        // configuration shape can vary and may include non-string/object
        // elements which we ignore.
        val children: Seq[PurchaseNode] = try {
          val underlying = c.underlying
          val list = underlying.getList("subcodes")
          // Convert Java list -> Scala and map each value depending on its type
          list.asScala.toSeq.map { v =>
            v.valueType() match {
              case ConfigValueType.STRING =>
                // Recursively parse a string child entry
                parseEntry(v.unwrapped().asInstanceOf[String])
              case ConfigValueType.OBJECT =>
                // Convert the ConfigObject to a Play Configuration then parse
                val obj = v.asInstanceOf[ConfigObject].toConfig
                parseEntry(Configuration(obj))
              case _ =>
                // Ignore unsupported list element types
                PurchaseNode("", "", "", Seq.empty)
            }
          }
        } catch {
          case _: Throwable => Seq.empty // parsing must not break startup
        }

        PurchaseNode(parent, code, label, children)

      // Fallback for any unexpected types
      case _ => PurchaseNode("", "", "", Seq.empty)
    }

    // iterate the top-level keys under purchase.mapping (country codes)
    val countries: Seq[String] = rootConfig.root().keySet().asScala.toSeq
    val playRoot = Configuration(rootConfig)

    countries.map { key =>
      // Each country key maps to a list which may contain plain strings or
      // nested objects. We try multiple strategies to get a Seq[Any] that we
      // can parse: prefer the underlying `Config` list (which preserves types)
      // but fall back to Play's `Configuration` helpers when necessary.
      val seqAny: Seq[Any] = try {
        // Use the typesafe `Config` API which exposes the underlying value
        // types directly. This handles mixed arrays gracefully.
        val list = rootConfig.getList(key)
        list.asScala.toSeq.map { v =>
          v.valueType() match {
            case ConfigValueType.STRING => v.unwrapped().asInstanceOf[String]
            case ConfigValueType.OBJECT =>
              // Convert nested ConfigObject into a Play Configuration for
              // uniform parsing below.
              val obj = v.asInstanceOf[ConfigObject].toConfig
              Configuration(obj)
            case _ => v.unwrapped()
          }
        }
      } catch {
        // If the underlying API path isn't present (eg when config is empty)
        // try progressively weaker reads using Play's `Configuration` APIs.
        case _: Throwable =>
          try {
            // Try reading as a Seq[String]
            playRoot.get[Seq[String]](key).map(_.asInstanceOf[Any])
          } catch {
            case _: Throwable =>
              try {
                // Try reading as Seq[Configuration]
                playRoot.get[Seq[Configuration]](key).map(_.asInstanceOf[Seq[Any]])
              } catch {
                case _: Throwable => Seq.empty
              }
          }
      }

      // Parse the (possibly mixed) list of entries into flat PurchaseNode items
      // using `parseEntry` which handles both string and object entries.
      val flatNodes = seqAny.map(parseEntry)

      /**
       * Normalise label keys by removing an ordering numeric segment when it
       * occurs directly after the type segment. Example:
       * `purchase.sub.fuel.1.10.5` -> `purchase.sub.fuel.10.5`.
       */
      def normalizeLabelKey(label: String): String = {
        if (label == null) return ""
        val prefix = "purchase.sub."
        if (!label.startsWith(prefix)) return label
        val rest = label.substring(prefix.length)
        val parts = rest.split("\\.")
        if (parts.length >= 2 && parts(1).matches("\\d+")) {
          val newRest = parts.head +: parts.drop(2)
          prefix + newRest.mkString(".")
        } else {
          label
        }
      }

      // build a two-level tree: base subcodes (first two segments) and their children
      val groupedByParent: Map[String, Seq[PurchaseNode]] = flatNodes.groupBy(_.parent)

      // Build a two-level tree for the country: a list of base subcodes
      // (first two dotted segments) each with their derived children. This
      // collapses deeper codes under their closest base so lookups are
      // straightforward for controllers that only need a single level of
      // subcategories.
      val nodes = groupedByParent.toSeq.flatMap { case (parentKey, nodesForParent) =>
        // compute base subcode keys (take first two dot segments)
        val baseKeys: Seq[String] = nodesForParent.map { n =>
          val parts = n.code.split("\\.")
          // base example: from "1.10.1" -> "1.10"
          parts.take(2).mkString(".")
        }.distinct

        baseKeys.map { base =>
          // Prefer an explicit label defined for the base; otherwise derive
          // a label from the first child's label by stripping the final
          // segment from a dotted message key.
          val explicitLabelOpt = nodesForParent.find(_.code == base).map(_.label)
          val derivedLabel = explicitLabelOpt.orElse {
            nodesForParent.find(n => n.code.startsWith(base + ".")).flatMap { child =>
              val l = child.label
              if (l != null && l.startsWith("purchase.sub.")) {
                val parts = l.split("\\.")
                // drop the last segment of the message key (child index)
                if (parts.length > 3) Some(parts.dropRight(1).mkString(".")) else None
              } else None
            }
          }
          val label = derivedLabel.getOrElse(base)

          // Children can come either from explicit `subcodes` inside a
          // parent object or from sibling entries whose code starts with
          // the base's prefix. Collect both sources and merge them.
          val explicitNodeOpt = nodesForParent.find(_.code == base)
          val explicitChildren: Seq[PurchaseNode] = explicitNodeOpt.toSeq.flatMap(_.children)

          val siblingChildren: Seq[PurchaseNode] = nodesForParent
            .filter(n => n.code != base && n.code.startsWith(base + "."))
            .map(n => PurchaseNode(parentKey, n.code, n.label, Seq.empty))

          // merge and deduplicate children by their code, then sort for
          // deterministic ordering in tests and views
          val children = (explicitChildren ++ siblingChildren).groupBy(_.code).map(_._2.head).toSeq.sortBy(_.code)

          PurchaseNode(parentKey, base, label, children)
        }
      }

      key -> nodes
    }.toMap
  } catch {
    case _: Throwable => Map.empty
  }

  def subcodesFor(country: String, parentKey: String): Seq[(String, String)] =
    nodesForCountry(country).toSeq.flatMap(_.filter(_.parent == parentKey).map(n => (n.code, n.label)))

  def subcodesFor(parentKey: String): Seq[(String, String)] =
    mapping.values.toSeq.flatten.filter(_.parent == parentKey).map(n => (n.code, n.label))

  def subcategoriesFor(country: String, parentKey: String, subcode: String): Seq[(String, String)] =
    nodesForCountry(country).toSeq.flatMap(_.filter(n => n.parent == parentKey && n.code == subcode).flatMap(_.children).map(c => (c.code, c.label)))

  private def nodesForCountry(country: String): Option[Seq[PurchaseNode]] = {
    if (country == null) return None
    val key = country.trim

    // Build a list of candidate keys to try. Handle common formats such as
    // "Name, CODE" or "CODE, Name" and cases where the stored value is a
    // full country name. Also try the final token after whitespace and a
    // two-letter alpha-2 fallback.
    val commaParts = key.split(",").map(_.trim).filter(_.nonEmpty)
    val spaceParts = key.split(" ").map(_.trim).filter(_.nonEmpty)

    // Candidate lookup keys we will try, in order. We include the raw key,
    // uppercase variants, tokens split on commas and spaces, and a final
    // two-letter fallback to increase the chance of matching a configured
    // country entry when the stored value may be a name, a code, or a
    // mixed string such as "Austria,AT".
    val candidates = Seq(
      key,
      key.toUpperCase,
      key.trim,
      key.trim.toUpperCase
    ) ++
      commaParts.reverse ++ // prefer part after comma (often the code)
      commaParts.reverse.map(_.toUpperCase) ++
      spaceParts.reverse ++ // prefer last space-delimited token
      spaceParts.reverse.map(_.toUpperCase) ++
      (if (key.length >= 2) Seq(key.takeRight(2).toUpperCase) else Seq.empty)

    candidates.iterator.flatMap(c => mapping.get(c)).toSeq.headOption
  }

  def buildRadioItems(options: Seq[(String, String)], msgs: play.api.i18n.Messages): Seq[RadioItem] =
    options.zipWithIndex.map { case ((code, labelKey), idx) =>
      def stripLeadingNumeric(key: String): String = {
        val parts = key.split("\\.")
        if (parts.length >= 4 && parts(0) == "purchase" && parts(1) == "sub") {
          (parts.take(3) ++ parts.drop(4)).mkString(".")
        } else key
      }

      def normalizeLabelKey(label: String): String = {
        if (label == null) return ""
        val prefix = "purchase.sub."
        if (!label.startsWith(prefix)) return label
        val rest = label.substring(prefix.length)
        val parts = rest.split("\\.")
        if (parts.length >= 3 && parts(1).matches("\\d+")) {
          val newRest = parts.head +: parts.drop(2)
          prefix + newRest.mkString(".")
        } else label
      }

      // Candidate message keys we will consult in order. The sequence
      // expresses a tolerant lookup strategy that handles several
      // configuration shapes and legacy key naming patterns:
      // 1. The original label key
      // 2. A label normalised relative to the code (drop code-first numeric)
      // 3. A label normalised by removing an ordering numeric segment
      // 4. A final stripped variant that drops a leading numeric fragment
      val candidates = Seq(
        labelKey,
        normalizeLabel(labelKey, code),
        normalizeLabelKey(labelKey),
        stripLeadingNumeric(labelKey)
      ).distinct

      // Try the regular Messages first, then fall back to purchase-specific message files
      val lang = Option(msgs.lang.code).getOrElse("en")

      def loadPurchaseMessages(langCode: String): Map[String, String] = {
        val namesToTry = Seq(s"messages.purchase.$langCode", "messages.purchase.en")

        namesToTry.iterator.flatMap { name =>
          try {
            env.resourceAsStream(name).map { is =>
              val src = Source.fromInputStream(is, "UTF-8")
              try {
                src.getLines().toSeq
                  .map(_.trim)
                  .filter(l => l.nonEmpty && !l.startsWith("#"))
                  .flatMap { line =>
                    val idx = line.indexOf('=')
                    if (idx > 0) Some(line.substring(0, idx).trim -> line.substring(idx + 1).trim)
                    else None
                  }
              } finally src.close()
            }.getOrElse(Seq.empty)
          } catch {
            case _: Throwable => Seq.empty
          }
        }.toSeq.foldLeft(Map.empty[String, String]) { case (acc, (k, v)) => acc + (k -> v) }
      }

      // Load purchase-specific message fallbacks from resource bundles named
      // `messages.purchase.<lang>`. If no resource is present we fall back to
      // the main `Messages` instance provided by Play.
      val purchaseMap = try loadPurchaseMessages(lang) catch { case _: Throwable => Map.empty[String, String] }

      // Resolve the first candidate that exists in either Play Messages or
      // the `messages.purchase.*` fallbacks. If none are defined default to
      // the provided label key string.
      val label = candidates.collectFirst({
        case k if msgs.isDefinedAt(k) => msgs(k)
        case k if purchaseMap.get(k).isDefined => purchaseMap(k)
      }).getOrElse(labelKey)
      RadioItem(
        content = uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text(label),
        value = Some(code),
        id = Some(s"value_$idx")
      )
    }
}
