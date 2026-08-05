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

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration

class ConfigPurchaseMappingSpec extends AnyWordSpec with Matchers {

  "ConfigPurchaseMapping parsing and helpers" should {
    "parse simple HOCON mapping and expose subcodes and subcategories" in {
      val confString = """
        |purchase.mapping = {
        |  AT = [
        |    "fuel|1|purchase.sub.fuel.1",
        |    "fuel|1.1|purchase.sub.fuel.1.1",
        |    "fuel|10|purchase.sub.fuel.10",
        |    "foodAndDrink|1|purchase.sub.foodAndDrink.1",
        |    "foodAndDrink|1.1|purchase.sub.foodAndDrink.1.1"
        |  ]
        |}
      """.stripMargin

      val cfg = Configuration(ConfigFactory.parseString(confString))
      val svc = new ConfigPurchaseMapping(cfg)

      val fuelCodes = svc.subcodesFor("AT", "fuel").map(_._1)
      fuelCodes should contain allElementsOf Seq("1", "1.1", "10")

      svc.subcategoriesFor("AT", "fuel", "1") should contain ("1.1" -> "purchase.sub.fuel.1.1")

      val foodCodes = svc.subcodesFor("foodAndDrink").map(_._1)
      foodCodes should contain allElementsOf Seq("1", "1.1")
    }

    "return empty mapping when key missing or invalid" in {
      val cfg = Configuration(ConfigFactory.parseString("""
        |other.mapping = { }
      """.stripMargin))

      val svc = new ConfigPurchaseMapping(cfg)

      svc.subcodesFor("DE", "fuel") shouldBe empty
      svc.subcodesFor("fuel") shouldBe empty
    }

    "ignore non-string/object list entries and not throw" in {
      val confString = """
        |purchase.mapping = {
        |  IT = [ 123, true, "fuel|1|purchase.sub.fuel.1" ]
        |}
      """.stripMargin

      val cfg = Configuration(ConfigFactory.parseString(confString))
      val svc = new ConfigPurchaseMapping(cfg)

      val subs = svc.subcodesFor("IT", "fuel").map(_._1)
      subs should contain ("1")
    }

    "ignore non-string/object entries in HOCON arrays" in {
      val confString = """
        |purchase.mapping = {
        |  GB = [
        |    "fuel|1|purchase.sub.fuel.1",
        |    { parent: "fuel", code: "1.1", label: "purchase.sub.fuel.1.1" },
        |    123
        |  ]
        |}
      """.stripMargin

      val cfg = Configuration(ConfigFactory.parseString(confString))
      val svc = new ConfigPurchaseMapping(cfg)

      val subs = svc.subcodesFor("GB", "fuel").map(_._1)
      subs should contain allElementsOf Seq("1", "1.1")
    }

    "handle mixed-depth codes when deriving children" in {
      val confString = """
        |purchase.mapping = {
        |  IE = [
        |    "fuel|1|purchase.sub.fuel.1",
        |    "fuel|1.1|purchase.sub.fuel.1.1",
        |    "fuel|1.10.1|purchase.sub.fuel.1.10.1"
        |  ]
        |}
      """.stripMargin

      val cfg = Configuration(ConfigFactory.parseString(confString))
      val svc = new ConfigPurchaseMapping(cfg)

      val subs = svc.subcodesFor("IE", "fuel").map(_._1)
      subs should contain allElementsOf Seq("1", "1.1", "1.10")

      // children for base '1' should include the deeper 1.1 and 1.10.1 entries
      val children = svc.subcategoriesFor("IE", "fuel", "1").map(_._1)
      children should contain allElementsOf Seq("1.1", "1.10.1")
    }

    "parse mixed nested subcodes arrays (strings + objects) and expose children" in {
      val confString = """
        |purchase.mapping = {
        |  DE = [
        |    { parent: "fuel", code: "1", label: "purchase.sub.fuel.1", subcodes: [ "fuel|1.1|purchase.sub.fuel.1.1", { parent: "fuel", code: "1.2", label: "purchase.sub.fuel.1.2" } ] }
        |  ]
        |}
      """.stripMargin

      val cfg = Configuration(ConfigFactory.parseString(confString))
      val svc = new ConfigPurchaseMapping(cfg)

      val subs = svc.subcodesFor("DE", "fuel").map(_._1)
      subs should contain ("1")

      val children = svc.subcategoriesFor("DE", "fuel", "1")
      children.map(_._1) should contain allElementsOf Seq("1.1", "1.2")
    }

    "subcodesFor without country returns aggregated results" in {
      val confString = """
        |purchase.mapping = {
        |  AT = [ "fuel|1|purchase.sub.fuel.1" ]
        |  DE = [ "fuel|1|purchase.sub.fuel.1" ]
      |}
      """.stripMargin

      val cfg = Configuration(ConfigFactory.parseString(confString))
      val svc = new ConfigPurchaseMapping(cfg)

      val all = svc.subcodesFor("fuel").map(_._1)
      all should contain("1")
    }
  }

  "buildRadioItems" should {
    "prefer stripped message keys when original contains redundant numeric segment" in {
      import play.api.i18n.{MessagesApi, MessagesImpl, Lang}
      import play.api.inject.guice.GuiceApplicationBuilder

      val app = GuiceApplicationBuilder().build()
      val msgsApi = app.injector.instanceOf[MessagesApi]
      val msgs = MessagesImpl(Lang("en"), msgsApi)

      val svc = new ConfigPurchaseMapping()

      val options = Seq(("1.10.1", "purchase.sub.fuel.1.10.1"))

      val items = svc.buildRadioItems(options, msgs)

      // compare rendered content string to avoid depending on viewmodel internals
      assert(items.head.content.toString.contains(msgs("purchase.sub.fuel.10.1")))
    }

    "fall back to the provided label string when no message keys match" in {
      import play.api.i18n.{MessagesApi, MessagesImpl, Lang}
      import play.api.inject.guice.GuiceApplicationBuilder

      val app = GuiceApplicationBuilder().build()
      val msgsApi = app.injector.instanceOf[MessagesApi]
      val msgs = MessagesImpl(Lang("en"), msgsApi)

      val svc = new ConfigPurchaseMapping()

      val options = Seq(("X", "Custom label here"))

      val items = svc.buildRadioItems(options, msgs)

      assert(items.head.content.toString.contains("Custom label here"))
    }

    "use normalizeLabel when code-first numeric matches" in {
      import play.api.i18n.{MessagesApi, MessagesImpl, Lang}
      import play.api.inject.guice.GuiceApplicationBuilder

      val app = GuiceApplicationBuilder().build()
      val msgsApi = app.injector.instanceOf[MessagesApi]
      val msgs = MessagesImpl(Lang("en"), msgsApi)

      val svc = new ConfigPurchaseMapping()

      // labelKey contains an extra numeric segment that should be removed by normalizeLabel
      val options = Seq(("1.10.1", "purchase.sub.fuel.1.10.1"))

      val items = svc.buildRadioItems(options, msgs)

      // message file contains purchase.sub.fuel.10.1, so normalization should resolve to that
      assert(items.head.content.toString.contains(msgs("purchase.sub.fuel.10.1")))
    }

    "prefer exact message key when present" in {
      import play.api.i18n.{MessagesApi, MessagesImpl, Lang}
      import play.api.inject.guice.GuiceApplicationBuilder

      val app = GuiceApplicationBuilder().build()
      val msgsApi = app.injector.instanceOf[MessagesApi]
      val msgs = MessagesImpl(Lang("en"), msgsApi)

      val svc = new ConfigPurchaseMapping()

      val options = Seq(("1.1", "purchase.sub.fuel.1.1"))

      val items = svc.buildRadioItems(options, msgs)

      assert(items.head.content.toString.contains(msgs("purchase.sub.fuel.1.1")))
    }

    "normalize dotted numeric segments when resolving message keys" in {
      import play.api.i18n.{MessagesApi, MessagesImpl, Lang}
      import play.api.inject.guice.GuiceApplicationBuilder

      val app = GuiceApplicationBuilder().build()
      val msgsApi = app.injector.instanceOf[MessagesApi]
      val msgs = MessagesImpl(Lang("en"), msgsApi)

      val svc = new ConfigPurchaseMapping()

      val options = Seq(("1.10.5", "purchase.sub.fuel.1.10.5"))

      val items = svc.buildRadioItems(options, msgs)

      // should resolve to the stripped form purchase.sub.fuel.10.5
      items.head.content.toString should include(msgs("purchase.sub.fuel.10.5"))
    }

    "strip leading numeric segment when resolving message keys" in {
      import play.api.i18n.{MessagesApi, MessagesImpl, Lang}
      import play.api.inject.guice.GuiceApplicationBuilder

      val app = GuiceApplicationBuilder().build()
      val msgsApi = app.injector.instanceOf[MessagesApi]
      val msgs = MessagesImpl(Lang("en"), msgsApi)

      val svc = new ConfigPurchaseMapping()

      val options = Seq(("1.10.1", "purchase.sub.fuel.1.10.1"))

      val items = svc.buildRadioItems(options, msgs)

      items.head.content.toString should include(msgs("purchase.sub.fuel.10.1"))
    }

    "prefer fallback label when normalizeLabelKey does not alter a short key" in {
      import play.api.i18n.{MessagesApi, MessagesImpl, Lang}
      import play.api.inject.guice.GuiceApplicationBuilder

      val app = GuiceApplicationBuilder().build()
      val msgsApi = app.injector.instanceOf[MessagesApi]
      val msgs = MessagesImpl(Lang("en"), msgsApi)

      val svc = new ConfigPurchaseMapping()

      // labelKey without extra numeric segments; none of the normalization helpers will change it
      val options = Seq(("X", "purchase.sub.simple.key"))

      val items = svc.buildRadioItems(options, msgs)

      // no message key exists for purchase.sub.simple.key in messages, so fallback to provided label string
      items.head.content.toString should include("purchase.sub.simple.key")
    }

    "prefer exact message key when present (candidate 1)" in {
      import play.api.i18n.{MessagesApi, MessagesImpl, Lang}
      import play.api.inject.guice.GuiceApplicationBuilder

      val app = GuiceApplicationBuilder().build()
      val msgsApi = app.injector.instanceOf[MessagesApi]
      val msgs = MessagesImpl(Lang("en"), msgsApi)

      val svc = new ConfigPurchaseMapping()

      // exact key exists in messages: purchase.sub.fuel.10.5
      val options = Seq(("1.10.5", "purchase.sub.fuel.10.5"))

      val items = svc.buildRadioItems(options, msgs)

      items.head.content.toString should include(msgs("purchase.sub.fuel.10.5"))
    }

    "resolve via normalizeLabelKey when normalizeLabel fails (candidate 3)" in {
      import play.api.i18n.{MessagesApi, MessagesImpl, Lang}
      import play.api.inject.guice.GuiceApplicationBuilder

      val app = GuiceApplicationBuilder().build()
      val msgsApi = app.injector.instanceOf[MessagesApi]
      val msgs = MessagesImpl(Lang("en"), msgsApi)

      val svc = new ConfigPurchaseMapping()

      // labelKey has a numeric second segment that doesn't match the code first segment
      // normalizeLabel will not change it, but normalizeLabelKey should strip the numeric segment
      val options = Seq(("1.10.5", "purchase.sub.fuel.2.10.5"))

      val items = svc.buildRadioItems(options, msgs)

      items.head.content.toString should include(msgs("purchase.sub.fuel.10.5"))
    }

    "parse configuration entries provided as strings (covered)" in {
      val confString = """
        |purchase.mapping = {
        |  FR = [
        |    "fuel|2|purchase.sub.fuel.2",
        |    "fuel|2.1|purchase.sub.fuel.2.1"
        |  ]
        |}
      """.stripMargin

      val cfg = Configuration(ConfigFactory.parseString(confString))
      val svc = new ConfigPurchaseMapping(cfg)

      val subs = svc.subcodesFor("FR", "fuel").map(_._1)
      subs should contain ("2")

      val children = svc.subcategoriesFor("FR", "fuel", "2")
      children should contain (("2.1", "purchase.sub.fuel.2.1"))
    }

    "parse configuration entries provided as nested objects with subcodes (covered)" in {
      val confString = """
        |purchase.mapping = {
        |  DE = [
        |    { parent: "fuel", code: "1", label: "purchase.sub.fuel.1", subcodes: [ { parent: "fuel", code: "1.1", label: "purchase.sub.fuel.1.1" } ] }
        |  ]
        |}
      """.stripMargin

      val cfg = Configuration(ConfigFactory.parseString(confString))
      val svc = new ConfigPurchaseMapping(cfg)

      val subs = svc.subcodesFor("DE", "fuel").map(_._1)
      subs should contain ("1")

      val children = svc.subcategoriesFor("DE", "fuel", "1")
      children should contain (("1.1", "purchase.sub.fuel.1.1"))
    }

    "return subcodes for parentKey without country (covered)" in {
      val confString = """
        |purchase.mapping = {
        |  ES = [
        |    "fuel|3|purchase.sub.fuel.3",
        |    "fuel|3.1|purchase.sub.fuel.3.1"
        |  ]
}
      """.stripMargin

      val cfg = Configuration(ConfigFactory.parseString(confString))
      val svc = new ConfigPurchaseMapping(cfg)

      val subs = svc.subcodesFor("fuel").map(_._1)
      subs should contain ("3")
    }
  }
}
