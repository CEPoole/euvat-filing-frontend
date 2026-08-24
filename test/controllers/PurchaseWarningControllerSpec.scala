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

package controllers

import base.SpecBase
import models.{CheckMode, NormalMode}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import views.html.PurchaseWarningView

class PurchaseWarningControllerSpec extends SpecBase {

  "PurchaseWarning Controller" - {

    "must return OK and the correct view for a GET in NormalMode" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.PurchaseWarningController.onPageLoad(NormalMode).url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseWarningView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(routes.DescribeItemsOnInvoiceController.onPageLoad(NormalMode), NormalMode)(request,
                                                                                                                           messages(application)
                                                                                                                          ).toString
      }
    }

    "must return OK and the correct view for a GET in CheckMode" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.PurchaseWarningController.onPageLoad(CheckMode).url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[PurchaseWarningView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(routes.DescribeItemsOnInvoiceController.onPageLoad(CheckMode), CheckMode)(request,
                                                                                                                         messages(application)
                                                                                                                        ).toString
      }
    }

    "must redirect to Journey Recovery for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(GET, routes.PurchaseWarningController.onPageLoad(NormalMode).url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }

    "must redirect to InvoiceTypeController on submit in NormalMode" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.PurchaseWarningController.onSubmit(NormalMode).url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.InvoiceTypeController.onPageLoad(NormalMode).url
      }
    }

    "must redirect to Check Your Purchase Details on submit in CheckMode" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(POST, routes.PurchaseWarningController.onSubmit(CheckMode).url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.purchase.routes.CheckYourPurchaseDetailsController.onPageLoad().url
      }
    }

    "must redirect to Journey Recovery for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      running(application) {
        val request = FakeRequest(POST, routes.PurchaseWarningController.onSubmit(NormalMode).url)

        val result = route(application, request).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
